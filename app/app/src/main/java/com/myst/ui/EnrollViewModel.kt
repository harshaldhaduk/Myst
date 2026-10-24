package com.myst.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.core.records.PeopleStore
import com.myst.core.records.Person
import com.myst.core.records.PhotoSource
import com.myst.features.faces.FamiliarFaces
import com.myst.features.faces.MockIdentity
import com.myst.features.faces.WhisperResult
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
)

class EnrollViewModel(application: Application) : AndroidViewModel(application) {
  val gateway = DatGlassesGateway(viewModelScope)
  private val mockKit = MockGlassesKit(application)
  private val store = PeopleStore(application)

  private val _ui = MutableStateFlow(EnrollUiState())
  val ui: StateFlow<EnrollUiState> = _ui.asStateFlow()

  val speaker = AndroidTtsSpeaker(application)
  val identity =
      MockIdentity(picked = { _ui.value.let { s -> s.people.firstOrNull { it.id == s.mockPickId } } })
  private val faces = FamiliarFaces(gateway, identity, speaker, people = { _ui.value.people })

  init {
    refreshPeople()
  }

  fun pickMockPerson(id: String?) = _ui.update { it.copy(mockPickId = id) }

  fun whoIsThis(requestPermission: suspend (Permission) -> PermissionStatus) {
    if (_ui.value.identifying || _ui.value.capturing) return
    _ui.update { it.copy(identifying = true) }
    viewModelScope.launch {
      val result = faces.whoIsThis(requestPermission)
      _ui.update { it.copy(identifying = false, lastWhisper = result) }
    }
  }

  override fun onCleared() {
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
