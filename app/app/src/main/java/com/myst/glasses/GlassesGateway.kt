package com.myst.glasses

import android.app.Activity
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.core.Capability
import kotlinx.coroutines.flow.StateFlow

/** A captured still, held in memory only. Callers decide whether a consented enrollment keeps it. */
class Photo(val jpeg: ByteArray, val width: Int, val height: Int)

/** Milliseconds for each step of one capture, shown on screen. */
data class CaptureTimings(
    val permissionMs: Long,
    val sessionMs: Long,
    val streamMs: Long,
    val captureMs: Long,
    val totalMs: Long,
)

sealed interface CaptureResult {
  data class Ok(val photo: Photo, val timings: CaptureTimings) : CaptureResult
  /** The wearer tapped, took the glasses off or closed the hinges. Normal; try again. */
  data object Paused : CaptureResult
  /** DeviceSessionError.CAPABILITY_DENIED. Normal even on supported hardware (META_SUITE 3.2). */
  data object CapabilityDenied : CaptureResult
  data object PermissionDenied : CaptureResult
  /** DAT_APP_ON_THE_GLASSES_UPDATE_REQUIRED. Wearables.openFirmwareUpdate() leads to the fix. */
  data object GlassesUpdateRequired : CaptureResult
  data class Failed(val reason: String) : CaptureResult
}

sealed interface GlassesState {
  data object NotInitialized : GlassesState
  data object Unregistered : GlassesState
  data object Registering : GlassesState
  /** Registered with the Meta AI app but no glasses connected. */
  data object Disconnected : GlassesState
  data class Connected(val deviceCount: Int) : GlassesState
  /** A capture session is open right now. */
  data object Capturing : GlassesState
}

interface GlassesGateway {
  val capability: StateFlow<Capability>
  val state: StateFlow<GlassesState>

  /** Hands off to the Meta AI app; it returns to us via the `myst://` scheme. */
  fun startRegistration(activity: Activity)

  fun startUnregistration(activity: Activity)

  /** Opens the Meta AI app's update screen for the glasses. Returns a failure reason or null. */
  fun openGlassesUpdate(activity: Activity): String?

  /**
   * One photo: camera.stop(), session.removeCamera(), then session.stop(). Uses a new session per
   * request unless a listening session is open (only one session runs per device).
   */
  suspend fun capturePhoto(
      requestPermission: suspend (Permission) -> PermissionStatus,
  ): CaptureResult
}
