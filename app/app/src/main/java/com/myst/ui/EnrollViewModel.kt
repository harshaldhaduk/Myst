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
import com.myst.features.conversation.LiveConversationRecorder
import com.myst.glasses.AndroidTtsSpeaker
import com.myst.glasses.CaptureResult
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
import kotlinx.coroutines.Job
import org.json.JSONArray
import org.json.JSONObject

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
    val liveConversationEnabled: Boolean = false,
    val liveConversationRecording: Boolean = false,
    val liveConversationReady: Boolean = false,
    val liveConversationStatus: String? = null,
    val liveConversationText: String = "",
    val liveConversationPartial: String = "",
    val liveConversationRecapRunning: Boolean = false,
    val liveConversationLastRecap: String? = null,
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
    _ui.update { it.copy(liveConversationEnabled =
        application.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) }
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
          gateway.startListening(
              requestPermission,
              onFinalText = { text -> handleVoiceCommand(text, requestPermission) },
              onCapTouch = { handleCapTouch(requestPermission) },
          )
      _ui.update {
        it.copy(
            message = if (failure == null) "Listening for \"Who am I looking at?\"" else null,
            voiceProblem = failure?.let(::plainVoiceProblem),
            needsGlassesUpdate = failure?.contains("DAT software needs an update", ignoreCase = true) == true,
        )
      }
    }
  }

  // MARK: - The one switch

  private var mystRequested = false

  /** Myst is on while DAT Speech listens or a live conversation transcript is active. */
  val mystOn: StateFlow<Boolean> =
      combine(gateway.isListening, _ui) { listening, state -> listening || state.liveConversationRecording }
          .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  /** One switch for DAT Speech and the experimental DAT touch input. */
  fun setMystOn(on: Boolean, requestPermission: suspend (Permission) -> PermissionStatus) {
    mystRequested = on
    if (on) setListening(true, requestPermission) else {
      stopLiveConversation()
      gateway.stopListening()
      _ui.update { it.copy(voiceProblem = null, message = "Myst is resting.") }
    }
  }

  private fun handleVoiceCommand(text: String, requestPermission: suspend (Permission) -> PermissionStatus) {
    when (VoiceCommands.match(text)) {
      VoiceCommands.Command.WHO_IS_THIS -> whoIsThis(requestPermission)
      VoiceCommands.Command.RECORD_CONVERSATION -> beginLiveConversation(requestPermission)
      null -> Unit
    }
  }

  private fun handleCapTouch(requestPermission: suspend (Permission) -> PermissionStatus) {
    val now = android.os.SystemClock.elapsedRealtime()
    if (now - lastGlassesTapAt < 1_500L) return
    lastGlassesTapAt = now
    if (_ui.value.liveConversationRecording) {
      _ui.update { it.copy(liveConversationStatus = "Glasses tap received. Preparing a recap…") }
      requestLiveConversationRecap()
    } else whoIsThis(requestPermission)
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

  fun enableLiveConversation() {
    _ui.update { it.copy(liveConversationEnabled = true, liveConversationStatus = null) }
  }

  fun liveConversationPermissionDenied() {
    _ui.update { it.copy(liveConversationEnabled = false,
        liveConversationStatus = "Microphone permission was denied; no conversation was recorded.") }
  }

  private var liveRecorder: LiveConversationRecorder? = null
  private var lastGlassesTapAt = 0L
  private var stoppedByVoice = false
  private val activeTurnsLock = Any()
  private val activeTurns = mutableListOf<String>()

  fun startLiveConversation(requestPermission: suspend (Permission) -> PermissionStatus) =
      beginLiveConversation(requestPermission)

  private fun beginLiveConversation(requestPermission: suspend (Permission) -> PermissionStatus) {
    val state = _ui.value
    if (state.liveConversationRecording) return
    if (!state.liveConversationEnabled) {
      _ui.update { it.copy(liveConversationStatus = "Record request heard. Enable live transcription on Home first.") }
      speakRecordStatus("Please enable live transcription on your phone.")
      return
    }
    if (getApplication<Application>().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED) {
      _ui.update { it.copy(liveConversationStatus = "Enable microphone access on Home before recording.") }
      return
    }
    if (!gateway.pauseSpeechForConversation()) {
      _ui.update { it.copy(liveConversationStatus = "Myst is not listening for the start phrase right now.") }
      return
    }
    val recorder = LiveConversationRecorder(getApplication(), museKeys::key)
    liveRecorder = recorder
    stoppedByVoice = false
    _ui.update { it.copy(liveConversationRecording = true, liveConversationReady = false,
        liveConversationPartial = "", liveConversationText = "",
        liveConversationLastRecap = null,
        liveConversationStatus = "Record request heard. Connecting the glasses microphone and Meta transcription…") }
    speaker.earcon()
    viewModelScope.launch {
      kotlinx.coroutines.delay(250) // Keep the first cue from being cut off by the HFP route switch.
      synchronized(activeTurnsLock) { activeTurns.clear() }
      try {
        recorder.run(
            onReady = {
              _ui.update { it.copy(liveConversationReady = true,
                  liveConversationStatus = "Recording now. Say ‘Stop recording’ to end.") }
              speaker.useCommunicationRoute(true)
              speaker.earcon()
              speakRecordStatus("Recording now.")
            },
            onPartial = { partial ->
              if (isStopRecordingPhrase(partial)) {
                stoppedByVoice = true
                _ui.update { it.copy(liveConversationStatus = "Stop request heard. Saving the transcript…",
                    liveConversationPartial = "") }
                true
              } else {
                _ui.update { it.copy(liveConversationPartial = partial) }
                false
              }
            },
            onTurn = { turn ->
              if (isStopRecordingPhrase(turn)) {
                stoppedByVoice = true
                _ui.update { it.copy(liveConversationStatus = "Finishing the transcript…", liveConversationPartial = "") }
                true
              } else {
                synchronized(activeTurnsLock) { activeTurns += turn }
                _ui.update { it.copy(liveConversationText = synchronized(activeTurnsLock) { activeTurns.joinToString(" ") },
                    liveConversationPartial = "") }
                false
              }
            },
            onFailure = { message -> _ui.update { it.copy(liveConversationStatus = message) } },
        )
      } finally {
        synchronized(activeTurnsLock) { activeTurns.clear() }
        liveRecorder = null
        speaker.useCommunicationRoute(false)
        val status = _ui.value.liveConversationStatus
        _ui.update { it.copy(liveConversationRecording = false, liveConversationReady = false,
            liveConversationText = "", liveConversationPartial = "", liveConversationStatus =
                if (stoppedByVoice) "Recording stopped." else status ?: "Recording ended.") }
        if (stoppedByVoice) speakRecordStatus("Recording stopped.")
        if (mystRequested) {
          val failure = gateway.resumeSpeechAfterConversation(
              requestPermission,
              onFinalText = { text -> handleVoiceCommand(text, requestPermission) },
              onCapTouch = { handleCapTouch(requestPermission) },
          )
          _ui.update { it.copy(voiceProblem = failure?.let(::plainVoiceProblem)) }
        }
      }
    }
  }

  private fun isStopRecordingPhrase(text: String): Boolean {
    val normalized = text.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
    return normalized.contains("stop recording") || normalized.contains("stop the recording")
  }

  private fun speakRecordStatus(text: String) {
    val guarded = ResponseGuard.check(text, null, _ui.value.people) as? ResponseGuard.Decision.Pass
    guarded?.line?.let(speaker::say)
  }

  fun stopLiveConversation() {
    liveRecorder?.stop()
    if (_ui.value.liveConversationRecording) _ui.update { it.copy(liveConversationStatus = "Ending the transcript…") }
  }

  fun requestLiveConversationRecap() {
    if (!_ui.value.liveConversationReady || _ui.value.liveConversationRecapRunning) return
    val text = (synchronized(activeTurnsLock) { activeTurns.joinToString(" ") } +
        " " + _ui.value.liveConversationPartial).trim()
    if (text.isBlank()) {
      speakRecordStatus("I haven't caught enough of the conversation yet.")
      _ui.update { it.copy(liveConversationStatus = "There is not enough transcript yet for a recap.") }
      return
    }
    speaker.earcon()
    _ui.update { it.copy(liveConversationRecapRunning = true,
        liveConversationStatus = "Preparing a one-sentence recap…") }
    viewModelScope.launch {
      val schema = JSONObject().put("type", "object")
          .put("properties", JSONObject().put("summary", JSONObject().put("type", "string"))
              .put("confident", JSONObject().put("type", "boolean")))
          .put("required", JSONArray().put("summary").put("confident"))
          .put("additionalProperties", false)
      val answer = muse.structured(
          "conversation recap",
          "Summarize the conversation transcript so far for its wearer. Return JSON with a single " +
              "natural second-person sentence (12 words or fewer) in summary, such as " +
              "'You were talking about tacos for dinner.' Use only facts stated in the transcript. " +
              "Do not use names, quote transcript fragments, mention recording, or give advice. " +
              "Set confident=true if a clear topic is present; false only when the transcript is unclear.",
          listOf(muse.textPart(text)), "conversation_recap", schema, maxTokens = 600,
      )
      val json = (answer as? MuseClient.Result.Ok)?.json
      val summary = json?.optString("summary")?.trim().orEmpty()
      val modelLine = if (json?.optBoolean("confident") == true && summary.isNotEmpty() &&
          summary.split(Regex("\\s+")).size <= 20) {
        _ui.value.people.fold(summary) { line, person ->
          Regex("\\b${Regex.escape(person.name)}\\b", RegexOption.IGNORE_CASE).replace(line, "someone")
        }
      } else null
      val guarded = modelLine?.let {
        ResponseGuard.check(it, null, _ui.value.people) as? ResponseGuard.Decision.Pass
      }
      val fallback = "I couldn't summarize that clearly yet. Please try again."
      if (guarded != null) speaker.say(guarded.line) else speakRecordStatus(fallback)
      _ui.update { it.copy(liveConversationRecapRunning = false,
          liveConversationLastRecap = guarded?.line?.text,
          liveConversationStatus = if (guarded != null) "Muse recap sent to the glasses."
              else "Muse could not make a clear recap; please try again.") }
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
    stopLiveConversation()
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
