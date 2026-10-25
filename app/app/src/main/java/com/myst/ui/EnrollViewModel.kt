package com.myst.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.core.records.HouseholdStore
import com.myst.core.records.Medication
import com.myst.core.records.Memory
import com.myst.core.records.DemoMemories
import com.myst.core.records.PeopleStore
import com.myst.core.records.Person
import com.myst.core.records.PhotoSource
import com.myst.BuildConfig
import com.myst.core.guard.ResponseGuard
import com.myst.features.faces.FamiliarFaces
import com.myst.core.records.Event
import com.myst.core.records.EventType
import com.myst.features.meds.DoseReminders
import com.myst.features.meds.DoseSchedule
import java.util.UUID
import com.myst.features.faces.MuseIdentity
import com.myst.muse.MuseClient
import com.myst.core.secrets.MuseKeyStore
import com.myst.features.faces.WhisperResult
import com.myst.features.VoiceCommands
import com.myst.features.conversation.ConversationSpike
import com.myst.glasses.AndroidTtsSpeaker
import com.myst.glasses.CaptureResult
import com.myst.glasses.GlassesTapTrigger
import com.myst.glasses.CaptureTimings
import com.myst.glasses.DatGlassesGateway
import com.myst.glasses.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingPhoto(val photo: Photo, val source: PhotoSource, val timings: CaptureTimings)

data class EnrollUiState(
    val datReady: Boolean = false,
    val capturing: Boolean = false,
    val message: String? = null,
    val pending: PendingPhoto? = null,
    val people: List<Person> = emptyList(),
    val identifying: Boolean = false,
    val lastWhisper: WhisperResult? = null,
    /** The glasses refused with DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED; offer the update screen. */
    val needsGlassesUpdate: Boolean = false,
    val medications: List<Medication> = emptyList(),
    val events: List<Event> = emptyList(),
    /** Android's "Alarms & reminders" access; without it reminders can run a few minutes late. */
    val exactAlarms: Boolean = true,
    /** The most recent thing Myst said (or chose not to say), for the Home screen. */
    val lastSaid: LastSaid? = null,
    /** Why voice listening isn't running while Myst is on, in plain words; null when it's fine. */
    val voiceProblem: String? = null,
    val conversationSpikeRunning: Boolean = false,
    val conversationSpikeReport: String? = null,
    val conversationSpikeTranscript: String? = null,
)

/** [quiet] means Myst stayed silent or was unsure; [note] explains a missing photo, if any. */
data class LastSaid(val text: String, val atMillis: Long, val quiet: Boolean, val note: String? = null)

class EnrollViewModel(application: Application) : AndroidViewModel(application) {
  private companion object {
    /** DAT may be initialized once per process; a recreated screen gets a new ViewModel. */
    @Volatile var datInitialized = false
    const val MAX_MEMORIES = 5
    const val MAX_MEMORY_CHARS = 140
  }

  val gateway = DatGlassesGateway(viewModelScope)
  private val store = PeopleStore(application)
  private val household = HouseholdStore(application)

  private val _ui = MutableStateFlow(EnrollUiState())
  val ui: StateFlow<EnrollUiState> = _ui.asStateFlow()

  val speaker = AndroidTtsSpeaker(application)

  /** The Muse key lives in the Android Keystore, provisioned by scripts/provision-muse-key.sh. */
  private val museKeys = MuseKeyStore(application)
  private val muse = MuseClient(museKeys::key)

  /** Null until checked; false means no key on this phone, so Muse calls cannot run. */
  private val _museKeySet = MutableStateFlow<Boolean?>(null)
  val museKeySet: StateFlow<Boolean?> = _museKeySet.asStateFlow()

  /** D12: only exists when a human built with myst.allowFaceId=true. The only identity provider. */
  val museIdentity: MuseIdentity? =
      if (BuildConfig.ALLOW_FACE_ID) {
        MuseIdentity(muse) {
          // Called on Dispatchers.IO by MuseIdentity. Reference photos: real glasses captures only,
          // never Mock Device Kit placeholders.
          _ui.value.people
              .filter { it.photoSource == PhotoSource.GLASSES }
              .mapNotNull { p -> store.photo(p)?.readBytes()?.let { p to it } }
        }
      } else null

  private val faces =
      FamiliarFaces(
          gateway,
          { museIdentity },
          speaker,
          people = { _ui.value.people },
          sightings = household::sightings,
          recordSighting = { id, at, detail ->
            household.logSighting(id, at, detail)
            refreshHousehold()
          },
      )

  init {
    refreshPeople()
    refreshHousehold()
    // Imports a freshly provisioned key (and wipes its plaintext file) at app start.
    viewModelScope.launch(Dispatchers.IO) { _museKeySet.value = museKeys.hasKey() }
    // Fast demo timing has no switch any more; make sure an old setting cannot stay on.
    if (DoseReminders.fastDemo(application)) DoseReminders.setFastDemo(application, false)
    _ui.update { it.copy(exactAlarms = DoseReminders.canScheduleExact(application)) }
    viewModelScope.launch(Dispatchers.IO) { DoseReminders.rescheduleAll(getApplication()) }
  }

  // MARK: - Dose reminders

  /**
   * Called when the app comes back to the front, e.g. from the exact-alarm settings screen.
   * Newly granted access re-plans the alarms so they become exact.
   */
  fun refreshExactAlarms() {
    val allowed = DoseReminders.canScheduleExact(getApplication())
    val wasAllowed = _ui.value.exactAlarms
    _ui.update { it.copy(exactAlarms = allowed) }
    if (allowed && !wasAllowed) viewModelScope.launch(Dispatchers.IO) { DoseReminders.rescheduleAll(getApplication()) }
  }

  /** R11/R12: log "reported taken" (D6), unless this dose was already reported. */
  fun markTaken(med: Medication) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val already =
          withContext(Dispatchers.IO) {
            val dose = DoseSchedule.currentDoseTime(med, now)
            val earlier = DoseSchedule.reportedTaken(med, dose, household.events())
            household.log(
                Event(
                    UUID.randomUUID().toString(),
                    if (earlier == null) EventType.DOSE_REPORTED_TAKEN else EventType.DOSE_BLOCKED_DOUBLE,
                    now,
                    medicationId = med.id,
                    detail = if (earlier == null) "marked on phone" else "second report on phone, not logged as taken",
                ),
            )
            earlier
          }
      _ui.update {
        it.copy(
            message =
                already?.let { e -> "Already reported taken at ${DoseSchedule.formatTimeOfDay(e.atMillis)}. Not logged again." }
                    ?: "${med.labelName}: reported taken at ${DoseSchedule.formatTimeOfDay(now)}.",
        )
      }
      refreshHousehold()
    }
  }

  // MARK: - Medication reminders (Lane 3 setup)

  fun saveMedication(draft: Medication) {
    if (draft.labelName.isBlank() || draft.pillsPerDose < 1) return
    viewModelScope.launch {
      val med =
          withContext(Dispatchers.IO) {
            // Cancel first: a shorter window can drop a stage whose old alarm would still fire.
            household.saveMedication(draft).also {
              DoseReminders.cancel(getApplication(), it.id)
              DoseReminders.rescheduleAll(getApplication())
            }
          }
      _ui.update { it.copy(message = "Saved the reminder for ${med.labelName}.") }
      refreshHousehold()
    }
  }

  fun deleteMedication(med: Medication) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        DoseReminders.cancel(getApplication(), med.id)
        household.deleteMedication(med.id)
      }
      _ui.update { it.copy(message = "Removed the reminder for ${med.labelName}.") }
      refreshHousehold()
    }
  }

  private fun refreshHousehold() {
    viewModelScope.launch {
      val (meds, events) = withContext(Dispatchers.IO) { household.medications() to household.events() }
      _ui.update { it.copy(medications = meds, events = events) }
    }
  }

  fun openGlassesUpdate(activity: Activity) {
    gateway.openGlassesUpdate(activity)?.let { f ->
      _ui.update { it.copy(message = "Could not open the update screen: $f") }
    }
  }

  /** Turns the spoken trigger on or off. Called from a tap (the mic permission needs a user action). */
  fun setListening(on: Boolean, requestPermission: suspend (Permission) -> PermissionStatus) {
    if (!on) {
      gateway.stopListening()
      _ui.update { it.copy(voiceProblem = null) }
      return
    }
    viewModelScope.launch {
      val failure =
          gateway.startListening(requestPermission) { text ->
            when (VoiceCommands.match(text)) {
              VoiceCommands.Command.WHO_IS_THIS -> whoIsThis(requestPermission)
              null -> Unit
            }
          }
      _ui.update {
        it.copy(
            message = if (failure == null) "Listening for \"Who is this?\"" else null,
            voiceProblem = failure?.let(::plainVoiceProblem),
            needsGlassesUpdate = failure?.contains("DAT software needs an update", ignoreCase = true) == true,
        )
      }
    }
  }

  // MARK: - Glasses tap trigger (silent "Who is this?")

  private var tapTrigger: GlassesTapTrigger? = null
  private val _tapOn = MutableStateFlow(false)
  val tapOn: StateFlow<Boolean> = _tapOn.asStateFlow()

  fun setTapTrigger(on: Boolean, requestPermission: suspend (Permission) -> PermissionStatus) {
    tapTrigger?.stop()
    tapTrigger = null
    if (on) {
      tapTrigger = GlassesTapTrigger(getApplication()) { whoIsThis(requestPermission) }.also { it.start() }
    }
    _tapOn.value = on
    _ui.update { it.copy(message = if (on) "Double-tap the glasses to ask \"Who is this?\" (experimental)." else "Glasses tap off.") }
  }

  // MARK: - The one switch

  /** Myst is on when it is listening for a phrase or a glasses tap. */
  val mystOn: StateFlow<Boolean> =
      combine(gateway.isListening, _tapOn) { listening, tap -> listening || tap }
          .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  /**
   * One switch for everything the wearer uses: listen for "Who is this?" and answer a tap on the
   * glasses. Face recognition runs on each request only when this build allows it (D12).
   * Called from a tap, because the glasses mic permission needs a user action.
   */
  fun setMystOn(on: Boolean, requestPermission: suspend (Permission) -> PermissionStatus) {
    setTapTrigger(on, requestPermission)
    setListening(on, requestPermission)
    if (!on) _ui.update { it.copy(message = "Myst is resting.") }
  }

  private fun plainVoiceProblem(failure: String): String =
      when {
        failure.contains("UNAVAILABLE", ignoreCase = true) ->
            "Voice didn't start: the glasses didn't answer. Make sure they're on, then try again."
        failure.contains("DAT software needs an update", ignoreCase = true) -> "Voice didn't start: the glasses' DAT app needs an update."
        failure.contains("permission", ignoreCase = true) || failure.contains("denied", ignoreCase = true) ->
            "Voice didn't start: allow the microphone for Myst in the Meta AI app."
        else -> "Voice didn't start ($failure). Try again."
      }

  fun clearMessage() = _ui.update { it.copy(message = null) }

  fun conversationSpikeDenied() {
    _ui.update { it.copy(conversationSpikeReport = "Microphone permission denied; no recording started.") }
  }

  fun runConversationSpike(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.conversationSpikeRunning) return
    _ui.update { it.copy(conversationSpikeRunning = true, conversationSpikeReport = "Routing HFP…", conversationSpikeTranscript = null) }
    viewModelScope.launch {
      val result = ConversationSpike(getApplication(), museKeys::key).run(gateway, requestPermission, muse) {
        _ui.update { it.copy(conversationSpikeReport = "Recording now. Say ‘Who is this?’ then talk with the other person. Photo check at 10 seconds.") }
      }
      _ui.update { it.copy(conversationSpikeRunning = false, conversationSpikeReport = result.report, conversationSpikeTranscript = result.transcript) }
      kotlinx.coroutines.delay(90_000)
      _ui.update { it.copy(conversationSpikeTranscript = null) }
    }
  }

  fun whoIsThis(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.identifying || _ui.value.capturing) return
    _ui.update { it.copy(identifying = true) }
    speaker.earcon()
    viewModelScope.launch {
      val result = faces.whoIsThis(requestPermission)
      _ui.update {
        it.copy(
            identifying = false,
            lastWhisper = result,
            lastSaid =
                LastSaid(
                    text = result.spoken ?: "Stayed quiet: the answer didn't pass the safety check.",
                    atMillis = System.currentTimeMillis(),
                    quiet = result.spoken == null || result.spoken in FamiliarFaces.NO_NAME_LINES,
                    note = result.note,
                ),
            needsGlassesUpdate = result.glassesUpdateRequired,
        )
      }
    }
  }

  override fun onCleared() {
    tapTrigger?.stop()
    gateway.stopListening()
    speaker.shutdown()
    super.onCleared()
  }

  /** After Android Bluetooth permissions are granted. Initialize exactly once per process. */
  fun onAndroidPermissions(granted: Boolean) {
    if (!granted) {
      _ui.update { it.copy(message = "Bluetooth permission is required to reach the glasses.") }
      return
    }
    if (_ui.value.datReady) return
    if (datInitialized) {
      // The process already initialized DAT (the screen was recreated); only start watching again.
      gateway.startMonitoring()
      _ui.update { it.copy(datReady = true) }
      return
    }
    Wearables.initialize(getApplication())
        .fold(
            {
              datInitialized = true
              gateway.startMonitoring()
              _ui.update { it.copy(datReady = true) }
            },
            { error, _ ->
              _ui.update { it.copy(message = "Couldn't start the glasses toolkit: ${error.description}") }
            },
        )
  }

  fun capture(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.capturing) return
    _ui.update { it.copy(capturing = true, message = null) }
    viewModelScope.launch {
      val source = PhotoSource.GLASSES
      val result = gateway.capturePhoto(requestPermission)
      _ui.update {
        when (result) {
          is CaptureResult.Ok ->
              it.copy(
                  capturing = false,
                  needsGlassesUpdate = false,
                  pending = PendingPhoto(result.photo, source, result.timings),
                  message = null,
              )
          CaptureResult.Paused ->
              it.copy(
                  capturing = false,
                  message = "Glasses paused the session (tap, removed or hinges closed). Try again.",
              )
          CaptureResult.CapabilityDenied ->
              it.copy(
                  capturing = false,
                  message = "The glasses denied camera access for this session (CAPABILITY_DENIED).",
              )
          CaptureResult.PermissionDenied ->
              it.copy(
                  capturing = false,
                  message = "Camera permission not granted in the Meta AI app.",
              )
          CaptureResult.GlassesUpdateRequired ->
              it.copy(
                  capturing = false,
                  needsGlassesUpdate = true,
                  message = "The glasses refused: their DAT software needs an update.",
              )
          is CaptureResult.Failed ->
              it.copy(capturing = false, message = "Capture failed: ${result.reason}")
        }
      }
    }
  }

  fun discardPhoto() = _ui.update { it.copy(pending = null) }

  /** Saves only with recorded consent; the UI cannot reach this otherwise. */
  fun enroll(name: String, relationship: String, consentRecordedBy: String) {
    val pending = _ui.value.pending
    if (name.isBlank() || consentRecordedBy.isBlank()) return
    viewModelScope.launch {
      val person =
          withContext(Dispatchers.IO) {
            store.enroll(name, relationship, consentRecordedBy, pending?.photo?.jpeg, pending?.source)
          }
      _ui.update { it.copy(pending = null, message = "Saved ${person.name} on this phone.") }
      refreshPeople()
    }
  }

  /**
   * Caregiver adds a memory (R14). It is checked like a spoken line now, so a memory the guard
   * would block (a banned phrase, a question, another family member's name) is refused here
   * instead of silencing the whole whisper later.
   */
  fun addMemory(person: Person, text: String) {
    val clean = text.trim().replace(Regex("\\s+"), " ")
    val problem =
        when {
          clean.isEmpty() -> "Type the memory first."
          clean.length > MAX_MEMORY_CHARS -> "Keep it under $MAX_MEMORY_CHARS characters."
          person.memories.count { !DemoMemories.isDemo(it) } >= MAX_MEMORIES ->
              "Up to $MAX_MEMORIES caregiver memories per person; delete one first."
          else ->
              (ResponseGuard.check(clean, person, _ui.value.people) as? ResponseGuard.Decision.Block)
                  ?.let { "Can't be spoken: ${it.reason}." }
        }
    if (problem != null) {
      _ui.update { it.copy(message = problem) }
      return
    }
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.addMemory(person.id, Memory(clean, person.consent.recordedBy)) }
      val long = clean.split(" ").size > 10
      _ui.update {
        it.copy(
            message =
                "Saved a memory for ${person.name}." +
                    if (long) " Long ones are skipped when the whisper would pass ${ResponseGuard.MAX_WORDS} words." else "",
        )
      }
      refreshPeople()
    }
  }

  fun removeMemory(person: Person, index: Int) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.removeMemory(person.id, index) }
      refreshPeople()
    }
  }

  fun makeFirstMemory(person: Person, index: Int) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.makeFirstMemory(person.id, index) }
      refreshPeople()
    }
  }

  fun revoke(person: Person) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        store.revoke(person.id)
        household.deleteSightings(person.id)
      }
      _ui.update { it.copy(message = "Removed ${person.name} and their photo.") }
      refreshPeople()
    }
  }

  fun photoBytes(person: Person): ByteArray? = store.photo(person)?.readBytes()

  private fun refreshPeople() {
    viewModelScope.launch {
      val people = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) store.seedDemoMemories() else store.all()
      }
      _ui.update { it.copy(people = people) }
    }
  }
}
