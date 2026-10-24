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
    private const val MAX_EDGE_PX = 1024
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

  private suspend fun captureOnce(
      requestPermission: suspend (Permission) -> PermissionStatus,
  ): CaptureResult {
    val t0 = SystemClock.elapsedRealtime()

    // 1. Camera permission, granted per device in the Meta AI app.
    val check = Wearables.checkPermissionStatus(Permission.CAMERA)
    val current = check.getOrNull() ?: return CaptureResult.Failed(check.describeError())
    if (current != PermissionStatus.Granted &&
        requestPermission(Permission.CAMERA) != PermissionStatus.Granted) {
      return CaptureResult.PermissionDenied
    }
    val tPermission = SystemClock.elapsedRealtime()

    // 2. A fresh session for this one request; sessions cannot be restarted.
    val created = Wearables.createSession(AutoDeviceSelector())
    val session =
        created.getOrNull()
            ?: return created.errorOrNull()?.let(::mapSessionError)
                ?: CaptureResult.Failed(created.describeError())
    val sessionError = MutableStateFlow<DeviceSessionError?>(null)
    val errorJob = scope.launch { session.errors.collect { sessionError.value = it } }
    var camera: Camera? = null
    try {
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
          } ?: return CaptureResult.Failed("Session did not start within ${SESSION_TIMEOUT_MS}ms")
      started.second?.let { return mapSessionError(it) }
      when (started.first) {
        DeviceSessionState.PAUSED -> return CaptureResult.Paused
        DeviceSessionState.STOPPED -> return CaptureResult.Failed("Session stopped before start")
        else -> Unit
      }
      val tSession = SystemClock.elapsedRealtime()

      // 3. Camera stream, needed for stream.capturePhoto() (the non-experimental photo path).
      val added =
          session.addCamera(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15))
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
      // 5. Documented shutdown order for 1.0.1: camera, then session.
      runCatching { camera?.stop() }.onFailure { Log.w(TAG, "camera.stop failed", it) }
      runCatching { session.stop() }.onFailure { Log.w(TAG, "session.stop failed", it) }
      errorJob.cancel()
    }
  }

  private fun DatResult<*, *>.describeError(): String =
      errorOrNull()?.description ?: exceptionOrNull()?.message ?: "unknown error"

  private fun mapSessionError(error: DeviceSessionError): CaptureResult =
      when (error) {
        DeviceSessionError.CAPABILITY_DENIED -> CaptureResult.CapabilityDenied
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
