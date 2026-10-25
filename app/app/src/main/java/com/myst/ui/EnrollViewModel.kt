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
import com.myst.core.records.PeopleStore
import com.myst.core.records.Place
import com.myst.core.records.Person
import com.myst.core.records.PhotoSource
import com.myst.BuildConfig
import com.myst.core.guard.ResponseGuard
import com.myst.features.faces.FamiliarFaces
import com.myst.core.records.Event
import com.myst.core.records.EventType
import com.myst.features.meds.BottleCheck
import com.myst.features.meds.DoseReminders
import com.myst.features.meds.DoseSchedule
import com.myst.features.meds.MedicineSummary
import java.util.UUID
import com.myst.features.faces.IdentityProvider
import com.myst.features.faces.MuseIdentity
import com.myst.muse.MuseClient
import com.myst.features.faces.MockIdentity
import com.myst.features.faces.WhisperResult
import com.myst.features.VoiceCommands
import com.myst.glasses.AndroidTtsSpeaker
import com.myst.glasses.CaptureResult
import com.myst.glasses.CaptureTimings
import com.myst.glasses.DatGlassesGateway
import com.myst.glasses.MockGlassesKit
import com.myst.glasses.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Demo Controls: who the operator says is in front of the wearer (null = not enrolled). */
    val mockPickId: String? = null,
    val identifying: Boolean = false,
    val lastWhisper: WhisperResult? = null,
    /** The glasses refused with DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED; offer the update screen. */
    val needsGlassesUpdate: Boolean = false,
    val places: List<Place> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val events: List<Event> = emptyList(),
    val checkingBottle: Boolean = false,
    val lastBottle: BottleCheckView? = null,
)

/** What the bottle test shows: both kinds of evidence and what was spoken. */
data class BottleCheckView(
    val spoken: String?,
    val photoMatch: String,
    val labelRead: String,
    val detail: String,
    val captureNote: String?,
)

class EnrollViewModel(application: Application) : AndroidViewModel(application) {
  val gateway = DatGlassesGateway(viewModelScope)
  private val mockKit = MockGlassesKit(application)
  private val store = PeopleStore(application)
  private val household = HouseholdStore(application)

  private val _ui = MutableStateFlow(EnrollUiState())
  val ui: StateFlow<EnrollUiState> = _ui.asStateFlow()

  val speaker = AndroidTtsSpeaker(application)
  val mockIdentity =
      MockIdentity(picked = { _ui.value.let { s -> s.people.firstOrNull { it.id == s.mockPickId } } })

  /** D12: only exists when a human built with myst.allowFaceId=true. */
  val museIdentity: MuseIdentity? =
      if (BuildConfig.ALLOW_FACE_ID) {
        MuseIdentity(MuseClient(BuildConfig.MUSE_API_KEY)) {
          // Reference photos: real glasses captures only, never Mock Device Kit placeholders.
          _ui.value.people
              .filter { it.photoSource == PhotoSource.GLASSES }
              .mapNotNull { p -> store.photo(p)?.readBytes()?.let { p to it } }
        }
      } else null

  private val _autoIdentify = MutableStateFlow(false)
  val autoIdentify: StateFlow<Boolean> = _autoIdentify.asStateFlow()

  fun setAutoIdentify(on: Boolean) {
    _autoIdentify.value = on && museIdentity != null
  }

  val identity: IdentityProvider
    get() = museIdentity?.takeIf { _autoIdentify.value } ?: mockIdentity

  private val faces = FamiliarFaces(gateway, { identity }, speaker, people = { _ui.value.people })

  init {
    refreshPeople()
    refreshHousehold()
    viewModelScope.launch(Dispatchers.IO) { DoseReminders.rescheduleAll(getApplication()) }
  }

  // MARK: - Dose reminders

  private val _fastDemo = MutableStateFlow(DoseReminders.fastDemo(application))
  val fastDemo: StateFlow<Boolean> = _fastDemo.asStateFlow()

  fun setFastDemo(on: Boolean) {
    DoseReminders.setFastDemo(getApplication(), on)
    _fastDemo.value = on
    viewModelScope.launch(Dispatchers.IO) { DoseReminders.rescheduleAll(getApplication()) }
  }

  fun exactAlarmsAllowed(): Boolean = DoseReminders.canScheduleExact(getApplication())

  /** R15 demo control: the reminder now, with re-reminders timed from now. */
  fun remindNow(med: Medication) {
    viewModelScope.launch(Dispatchers.IO) { DoseReminders.remindNow(getApplication(), med) }
    _ui.update { it.copy(message = "Reminder for ${med.labelName} fired now.") }
  }

  /** R11/R12: log "reported taken" (D6), unless this dose was already reported. */
  fun markTaken(med: Medication) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val already =
          withContext(Dispatchers.IO) {
            val dose = DoseSchedule.currentDoseTime(med, now)
            DoseSchedule.reportedTaken(med, dose, household.events())
                ?: household.log(
                    Event(UUID.randomUUID().toString(), EventType.DOSE_REPORTED_TAKEN, now, medicationId = med.id, detail = "marked on phone"),
                ).let { null }
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

  // MARK: - Places and medications (Lane 3 setup)

  /** The pending glasses photo, if any, becomes a reference photo for [savePlace]/[saveMedication]. */
  private fun takePendingPhoto(attach: Boolean): List<ByteArray> =
      _ui.value.pending?.takeIf { attach }?.photo?.jpeg?.let { listOf(it) } ?: emptyList()

  fun savePlace(id: String?, name: String, note: String, attachPhoto: Boolean) {
    if (name.isBlank()) return
    val photos = takePendingPhoto(attachPhoto)
    viewModelScope.launch {
      val place = withContext(Dispatchers.IO) { household.savePlace(id, name, note, photos) }
      _ui.update {
        it.copy(
            pending = if (photos.isNotEmpty()) null else it.pending,
            message = "Saved ${place.name} (${place.photoFiles.size} photo${if (place.photoFiles.size == 1) "" else "s"}).",
        )
      }
      refreshHousehold()
    }
  }

  fun deletePlace(place: Place) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { household.deletePlace(place.id) }
      _ui.update { it.copy(message = "Removed ${place.name} and its photos.") }
      refreshHousehold()
    }
  }

  fun saveMedication(draft: Medication, attachPhoto: Boolean) {
    if (draft.labelName.isBlank() || draft.pillsPerDose < 1) return
    val photos = takePendingPhoto(attachPhoto)
    viewModelScope.launch {
      val med =
          withContext(Dispatchers.IO) {
            household.saveMedication(draft, photos).also { DoseReminders.rescheduleAll(getApplication()) }
          }
      _ui.update {
        it.copy(
            pending = if (photos.isNotEmpty()) null else it.pending,
            message = "Saved ${med.labelName} (${med.bottlePhotoFiles.size} bottle photo${if (med.bottlePhotoFiles.size == 1) "" else "s"}).",
        )
      }
      refreshHousehold()
    }
  }

  fun deleteMedication(med: Medication) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        DoseReminders.cancel(getApplication(), med.id)
        household.deleteMedication(med.id)
      }
      _ui.update { it.copy(message = "Removed ${med.labelName} and its photos.") }
      refreshHousehold()
    }
  }

  fun householdPhoto(file: String): ByteArray? = household.photo(file)?.readBytes()

  private val bottleCheck = BottleCheck(MuseClient(BuildConfig.MUSE_API_KEY))

  /** Test tool for D14: one glasses photo -> photo match + label read -> spoken verdict. */
  fun checkBottle(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.checkingBottle || _ui.value.capturing) return
    _ui.update { it.copy(checkingBottle = true) }
    viewModelScope.launch {
      val capture = gateway.capturePhoto(requestPermission)
      val view =
          if (capture !is CaptureResult.Ok) {
            BottleCheckView(null, "-", "-", "", captureNote = "No photo: ${describe(capture)}")
          } else {
            val meds =
                withContext(Dispatchers.IO) {
                  _ui.value.medications.map { m ->
                    m to m.bottlePhotoFiles.mapNotNull { household.photo(it)?.readBytes() }
                  }
                }
            val r = bottleCheck.identify(capture.photo, meds)
            val lines =
                when (val v = r.verdict) {
                  // Name, schedule and food/water rule, all from the caregiver's records (D6).
                  is BottleCheck.Verdict.Identified ->
                      MedicineSummary.lines(
                          _ui.value.medications.filter { MedicineSummary.sameProduct(it, v.medication) },
                      )
                  BottleCheck.Verdict.Unsure ->
                      listOf("I can't tell which medicine that is. Please turn it so I can see the label.")
                }
            // Each line passes the guard on its own; a blocked line is dropped, never reworded.
            val spoken =
                lines
                    .mapNotNull { line ->
                      (ResponseGuard.check(line, null, _ui.value.people) as? ResponseGuard.Decision.Pass)
                          ?.line?.also(speaker::say)?.text
                    }
                    .joinToString(" ")
                    .ifEmpty { null }
            BottleCheckView(
                spoken = spoken,
                photoMatch =
                    if (!r.bottleVisible) "no medicine package seen"
                    else r.photoMatch?.let { "${it.first.labelName} (${"%.2f".format(it.second)})" } ?: "no match",
                labelRead =
                    r.labelText?.let { "\"$it\"" + (r.labelMatch?.let { m -> " -> ${m.labelName}" } ?: " -> not one of hers") }
                        ?: "unreadable",
                detail = r.detail,
                captureNote = null,
            )
          }
      _ui.update { it.copy(checkingBottle = false, lastBottle = view) }
    }
  }

  private fun describe(capture: CaptureResult): String =
      when (capture) {
        is CaptureResult.Ok -> "ok"
        CaptureResult.Paused -> "glasses paused"
        CaptureResult.CapabilityDenied -> "camera denied for this session"
        CaptureResult.PermissionDenied -> "no camera permission"
        CaptureResult.GlassesUpdateRequired -> "glasses need a software update"
        is CaptureResult.Failed -> capture.reason
      }

  private fun refreshHousehold() {
    viewModelScope.launch {
      val (places, meds, events) =
          withContext(Dispatchers.IO) { Triple(household.places(), household.medications(), household.events()) }
      _ui.update { it.copy(places = places, medications = meds, events = events) }
    }
  }

  fun pickMockPerson(id: String?) = _ui.update { it.copy(mockPickId = id) }

  fun openGlassesUpdate(activity: Activity) {
    gateway.openGlassesUpdate(activity)?.let { f ->
      _ui.update { it.copy(message = "Could not open the update screen: $f") }
    }
  }

  /** Turns the spoken trigger on or off. Called from a tap (the mic permission needs a user action). */
  fun setListening(on: Boolean, requestPermission: suspend (Permission) -> PermissionStatus) {
    if (!on) {
      gateway.stopListening()
      return
    }
    viewModelScope.launch {
      val failure =
          gateway.startListening(requestPermission) { text ->
            when (VoiceCommands.match(text)) {
              VoiceCommands.Command.WHO_IS_THIS -> whoIsThis(requestPermission)
              VoiceCommands.Command.WHAT_IS_THIS -> checkBottle(requestPermission)
              null -> Unit
            }
          }
      _ui.update {
        it.copy(
            message = failure?.let { f -> "Listening failed: $f" } ?: "Listening for \"Who is this?\" and \"What is this?\"",
            needsGlassesUpdate = failure?.contains("update") == true,
        )
      }
    }
  }

  /** MOCK: makes the simulated glasses "hear" the phrase. Mock Device Kit only. */
  fun simulateWhoIsThis() {
    mockKit.simulateSpeech("Who is this?")?.let { f -> _ui.update { it.copy(message = f) } }
  }

  fun whoIsThis(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.identifying || _ui.value.capturing) return
    _ui.update { it.copy(identifying = true) }
    viewModelScope.launch {
      val result = faces.whoIsThis(requestPermission)
      _ui.update {
        it.copy(
            identifying = false,
            lastWhisper = result,
            needsGlassesUpdate = result.glassesUpdateRequired,
        )
      }
    }
  }

  override fun onCleared() {
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
    Wearables.initialize(getApplication())
        .fold(
            {
              gateway.startMonitoring()
              _ui.update { it.copy(datReady = true) }
            },
            { error, _ ->
              _ui.update { it.copy(message = "DAT initialize failed: ${error.description}") }
            },
        )
  }

  fun setMockGlasses(enabled: Boolean) {
    val failure = if (enabled) mockKit.enable() else null.also { mockKit.disable() }
    gateway.setMockMode(enabled && failure == null)
    _ui.update {
      it.copy(
          message =
              failure
                  ?: if (enabled) "Mock glasses paired (simulated, no hardware)."
                  else "Mock glasses off.",
      )
    }
  }

  fun capture(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.capturing) return
    _ui.update { it.copy(capturing = true, message = null) }
    viewModelScope.launch {
      val source = if (mockKit.isEnabled) PhotoSource.MOCK_DEVICE_KIT else PhotoSource.GLASSES
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

  fun revoke(person: Person) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.revoke(person.id) }
      _ui.update { it.copy(message = "Removed ${person.name} and their photo.") }
      refreshPeople()
    }
  }

  fun photoBytes(person: Person): ByteArray? = store.photo(person)?.readBytes()

  private fun refreshPeople() {
    viewModelScope.launch {
      val people = withContext(Dispatchers.IO) { store.all() }
      _ui.update { it.copy(people = people) }
    }
  }
}
