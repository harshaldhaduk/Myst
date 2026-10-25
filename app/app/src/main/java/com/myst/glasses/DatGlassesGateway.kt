package com.myst.glasses

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.removeCamera
import com.meta.wearable.dat.speech.Speech
import com.meta.wearable.dat.speech.addSpeech
import com.meta.wearable.dat.speech.removeSpeech
import com.meta.wearable.dat.speech.types.SpeechState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DatResult
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.myst.core.Capability
import com.myst.core.Label
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one DAT implementation. Real glasses and the Mock Device Kit go through the same code; only
 * [MockGlassesKit] differs, by pairing a simulated device first. That keeps the mock testing the
 * path the glasses use, and the label tells the two apart.
 */
class DatGlassesGateway(private val scope: CoroutineScope) : GlassesGateway {
  companion object {
    private const val TAG = "Myst:Glasses"
    private const val SESSION_TIMEOUT_MS = 15_000L
    private const val STREAM_TIMEOUT_MS = 15_000L
    private const val ERROR_SETTLE_MS = 750L
    private const val SPEECH_RESTART_DELAY_MS = 300L
    private const val MAX_EDGE_PX = 1280
    private const val JPEG_QUALITY = 85

    val REAL =
        Capability(
            "Glasses camera",
            Label.LIVE,
            "DAT 1.0.1 photo from the paired Ray-Ban Meta glasses",
        )
    val MOCK =
        Capability(
            "Glasses camera",
            Label.MOCK,
            "DAT Mock Device Kit: simulated glasses, placeholder image",
        )
  }

  private val _capability = MutableStateFlow(REAL)
  override val capability: StateFlow<Capability> = _capability.asStateFlow()

  private val _state = MutableStateFlow<GlassesState>(GlassesState.NotInitialized)
  override val state: StateFlow<GlassesState> = _state.asStateFlow()

  private val captureLock = Mutex()
  private var monitoring = false

  /** Call once, after Wearables.initialize() has succeeded. */
  fun startMonitoring() {
    if (monitoring) return
    monitoring = true
    scope.launch {
      combine(Wearables.registrationState, Wearables.devices) { reg, devices -> reg to devices }
          .collect { (reg, devices) ->
            if (_state.value == GlassesState.Capturing) return@collect
            _state.value =
                when (reg) {
                  RegistrationState.REGISTERED,
                  RegistrationState.UNREGISTERING ->
                      if (devices.isEmpty()) GlassesState.Disconnected
                      else GlassesState.Connected(devices.size)
                  RegistrationState.REGISTERING -> GlassesState.Registering
                  else -> GlassesState.Unregistered
                }
          }
    }
  }

  fun setMockMode(enabled: Boolean) {
    _capability.value = if (enabled) MOCK else REAL
  }

  override fun startRegistration(activity: Activity) = Wearables.startRegistration(activity)

  override fun startUnregistration(activity: Activity) = Wearables.startUnregistration(activity)

  override fun openGlassesUpdate(activity: Activity): String? =
      Wearables.openFirmwareUpdate(activity).let { r -> if (r.isFailure) r.describeError() else null }

  override suspend fun capturePhoto(
      requestPermission: suspend (Permission) -> PermissionStatus,
  ): CaptureResult =
      captureLock.withLock {
        val previous = _state.value
        _state.value = GlassesState.Capturing
        try {
          captureOnce(requestPermission)
        } finally {
          _state.value = previous
        }
      }

  // MARK: - Listening (DAT Speech, experimental)

  /** One open session with Speech attached; captures borrow it instead of opening their own. */
  private class Listening(val session: OpenSession, val speech: Speech, val jobs: List<Job>)

  private var listening: Listening? = null
  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  /**
   * Starts on-glasses speech recognition. [onFinalText] gets each final transcript; callers match
   * the phrase and must not store or display other speech. Must follow a user action (the
   * microphone permission request opens the Meta AI app).
   */
  suspend fun startListening(
      requestPermission: suspend (Permission) -> PermissionStatus,
      onFinalText: (String) -> Unit,
  ): String? =
      captureLock.withLock {
        if (listening != null) return@withLock null
        ensurePermission(Permission.MICROPHONE, requestPermission)?.let { return@withLock it.reason() }
        val opened =
            when (val o = openSession()) {
              is Opened.Ok -> o.session
              is Opened.Err -> return@withLock o.result.reason()
            }
        val added = opened.session.addSpeech()
        val speech =
            added.getOrNull()
                ?: run {
                  opened.close()
                  return@withLock "Speech: ${added.describeError()}"
                }
        val jobs =
            listOf(
                scope.launch {
                  speech.transcriptions.collect { result ->
                    if (result != null && result.isFinal) onFinalText(result.text)
                  }
                },
                // On the real glasses a speech run ends on its own after about 60 s
                // ("Speech session completed"). Restart it while Listening is on; give up on failure.
                scope.launch {
                  var running = false
                  speech.state.collect { state ->
                    if (state == SpeechState.STARTED) running = true
                    if (state == SpeechState.STOPPED && running && listening?.speech === speech) {
                      running = false
                      delay(SPEECH_RESTART_DELAY_MS)
                      if (speech.start().isFailure) stopListening()
                    }
                  }
                },
                // The wearer can end the session (hinges closed, glasses removed); stop cleanly.
                scope.launch {
                  opened.session.state.first { it == DeviceSessionState.STOPPED }
                  stopListening()
                },
            )
        val start = speech.start()
        if (start.isFailure) {
          jobs.forEach { it.cancel() }
          runCatching { opened.session.removeSpeech() }
          opened.close()
          return@withLock "Speech: ${start.describeError()}"
        }
        listening = Listening(opened, speech, jobs)
        _isListening.value = true
        null
      }

  fun stopListening() {
    val l = listening ?: return
    listening = null
    _isListening.value = false
    l.jobs.forEach { it.cancel() }
    runCatching { l.speech.stop() }
    runCatching { l.session.session.removeSpeech() }
    l.session.close()
  }

  // MARK: - Capture

  private class OpenSession(val session: DeviceSession, val errorJob: Job) {
    fun close() {
      runCatching { session.stop() }.onFailure { Log.w(TAG, "session.stop failed", it) }
      errorJob.cancel()
    }
  }

  private sealed interface Opened {
    data class Ok(val session: OpenSession) : Opened
    data class Err(val result: CaptureResult) : Opened
  }

  /** A fresh session, started; sessions cannot be restarted once stopped. */
  private suspend fun openSession(): Opened {
    val created = Wearables.createSession(AutoDeviceSelector())
    val session =
        created.getOrNull()
            ?: return Opened.Err(
                created.errorOrNull()?.let(::mapSessionError)
                    ?: CaptureResult.Failed(created.describeError()),
            )
    val sessionError = MutableStateFlow<DeviceSessionError?>(null)
    val allErrors = mutableListOf<DeviceSessionError>()
    val open =
        OpenSession(
            session,
            scope.launch {
              session.errors.collect {
                allErrors += it
                sessionError.value = it
              }
            },
        )
    session.start()
    val started =
        withTimeoutOrNull(SESSION_TIMEOUT_MS) {
          combine(session.state, sessionError) { s, e -> s to e }
              .first { (s, e) ->
                e != null ||
                    s == DeviceSessionState.STARTED ||
                    s == DeviceSessionState.PAUSED ||
                    s == DeviceSessionState.STOPPED
              }
        }
    val failure =
        when {
          started == null -> CaptureResult.Failed("Session did not start within ${SESSION_TIMEOUT_MS}ms")
          started.second != null -> mapSessionError(started.second!!)
          started.first == DeviceSessionState.PAUSED -> CaptureResult.Paused
          started.first == DeviceSessionState.STOPPED ->
              CaptureResult.Failed("Session stopped before start")
          else -> null
        }
    if (failure != null) {
      // The glasses often send a generic "ended by device" first and the real cause (for example
      // DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED) a moment later; wait briefly and prefer the cause.
      delay(ERROR_SETTLE_MS)
      open.close()
      val specific =
          allErrors.firstOrNull { it == DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED }
              ?: allErrors.firstOrNull { it != DeviceSessionError.SESSION_ENDED_BY_DEVICE }
      return Opened.Err(specific?.let(::mapSessionError) ?: failure)
    }
    return Opened.Ok(open)
  }

  /** Null when granted; otherwise the outcome to return. */
  private suspend fun ensurePermission(
      permission: Permission,
      requestPermission: suspend (Permission) -> PermissionStatus,
  ): CaptureResult? {
    val check = Wearables.checkPermissionStatus(permission)
    val current = check.getOrNull() ?: return CaptureResult.Failed(check.describeError())
    if (current != PermissionStatus.Granted && requestPermission(permission) != PermissionStatus.Granted) {
      return CaptureResult.PermissionDenied
    }
    return null
  }

  private suspend fun captureOnce(
      requestPermission: suspend (Permission) -> PermissionStatus,
  ): CaptureResult {
    val t0 = SystemClock.elapsedRealtime()

    // 1. Camera permission, granted per device in the Meta AI app.
    ensurePermission(Permission.CAMERA, requestPermission)?.let { return it }
    val tPermission = SystemClock.elapsedRealtime()

    // 2. Reuse the listening session if there is one (one session per device), else open one.
    val borrowed = listening?.session
    val owned: OpenSession? =
        if (borrowed != null) null
        else
            when (val o = openSession()) {
              is Opened.Ok -> o.session
              is Opened.Err -> return o.result
            }
    val session = (borrowed ?: owned!!).session
    var camera: Camera? = null
    try {
      val tSession = SystemClock.elapsedRealtime()

      // 3. Camera stream, needed for stream.capturePhoto() (the non-experimental photo path).
      val added =
          session.addCamera(StreamConfiguration(videoQuality = VideoQuality.HIGH, frameRate = 15))
      val cam = added.getOrNull() ?: return CaptureResult.Failed("Camera: ${added.describeError()}")
      camera = cam
      // Subscribe to errors before start() so a failed start is reported now, not at the timeout.
      val streamError = MutableStateFlow<String?>(null)
      val streamErrorJob =
          scope.launch { cam.stream.errorStream.collect { streamError.value = it.description } }
      try {
        val streamStart = cam.stream.start()
        if (streamStart.isFailure) {
          return CaptureResult.Failed("Stream: ${streamStart.describeError()}")
        }
        val (streamState, error) =
            withTimeoutOrNull(STREAM_TIMEOUT_MS) {
              combine(cam.stream.state, streamError) { s, e -> s to e }
                  .first { (s, e) ->
                    e != null ||
                        s == StreamState.STREAMING ||
                        s == StreamState.PAUSED ||
                        s == StreamState.CLOSED
                  }
            } ?: return CaptureResult.Failed("Stream did not start within ${STREAM_TIMEOUT_MS}ms")
        if (error != null) return CaptureResult.Failed("Stream: $error")
        if (streamState == StreamState.PAUSED) return CaptureResult.Paused
        if (streamState == StreamState.CLOSED) return CaptureResult.Failed("Stream closed")
      } finally {
        streamErrorJob.cancel()
      }
      val tStream = SystemClock.elapsedRealtime()

      // 4. One photo.
      val shot = cam.stream.capturePhoto()
      val data = shot.getOrNull() ?: return CaptureResult.Failed("Capture: ${shot.describeError()}")
      val photo =
          withContext(Dispatchers.Default) { toJpeg(data) }
              ?: return CaptureResult.Failed("Could not decode the photo")
      val tCapture = SystemClock.elapsedRealtime()

      return CaptureResult.Ok(
          photo,
          CaptureTimings(
              permissionMs = tPermission - t0,
              sessionMs = tSession - tPermission,
              streamMs = tStream - tSession,
              captureMs = tCapture - tStream,
              totalMs = tCapture - t0,
          ),
      )
    } finally {
      // 5. Documented shutdown order: camera.stop(), session.removeCamera(), then session.stop()
      //    unless the session belongs to Listening, which keeps running.
      runCatching { camera?.stop() }.onFailure { Log.w(TAG, "camera.stop failed", it) }
      if (camera != null) runCatching { session.removeCamera() }
      owned?.close()
    }
  }

  private fun CaptureResult.reason(): String =
      when (this) {
        is CaptureResult.Failed -> reason
        CaptureResult.PermissionDenied -> "Permission not granted in the Meta AI app"
        CaptureResult.CapabilityDenied -> "The glasses denied this capability (CAPABILITY_DENIED)"
        CaptureResult.Paused -> "Glasses paused the session"
        CaptureResult.GlassesUpdateRequired -> "The glasses' DAT software needs an update"
        is CaptureResult.Ok -> "ok"
      }

  private fun DatResult<*, *>.describeError(): String =
      errorOrNull()?.description ?: exceptionOrNull()?.message ?: "unknown error"

  private fun mapSessionError(error: DeviceSessionError): CaptureResult =
      when (error) {
        DeviceSessionError.CAPABILITY_DENIED -> CaptureResult.CapabilityDenied
        DeviceSessionError.DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED -> CaptureResult.GlassesUpdateRequired
        DeviceSessionError.DWA_UNAVAILABLE,
        DeviceSessionError.NO_ELIGIBLE_DEVICE,
        DeviceSessionError.DEVICE_DISCONNECTED ->
            CaptureResult.Failed("Glasses not available: wear them unfolded and check the Meta AI app shows them connected")
        DeviceSessionError.SESSION_ENDED_BY_DEVICE ->
            CaptureResult.Failed("The glasses ended the session (not worn, or their software needs an update)")
        else -> CaptureResult.Failed(error.description)
      }

  /** Decode, apply EXIF rotation (glasses HEIC carries it as a tag), downscale, re-encode. */
  private fun toJpeg(data: PhotoData): Photo? {
    val bitmap =
        when (data) {
          is PhotoData.Bitmap -> data.bitmap
          is PhotoData.HEIC -> decodeHeic(data)
          else -> null
        } ?: return null
    val scale = MAX_EDGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
    val scaled =
        if (scale < 1f) {
          Bitmap.createScaledBitmap(
              bitmap,
              (bitmap.width * scale).toInt(),
              (bitmap.height * scale).toInt(),
              true,
          )
        } else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    return Photo(out.toByteArray(), scaled.width, scaled.height)
  }

  private fun decodeHeic(data: PhotoData.HEIC): Bitmap? {
    val buffer = data.data.duplicate().apply { rewind() }
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val orientation =
        try {
          ExifInterface(ByteArrayInputStream(bytes))
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
          ExifInterface.ORIENTATION_NORMAL
        }
    val degrees =
        when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_90 -> 90f
          ExifInterface.ORIENTATION_ROTATE_180 -> 180f
          ExifInterface.ORIENTATION_ROTATE_270 -> 270f
          else -> return bitmap
        }
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(degrees) },
        true,
    )
  }
}
