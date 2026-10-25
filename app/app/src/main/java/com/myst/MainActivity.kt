package com.myst

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECORD_AUDIO
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.ui.EnrollViewModel
import com.myst.ui.MystApp
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
  private val viewModel: EnrollViewModel by viewModels()

  private val androidPermissions =
      registerForActivityResult(RequestMultiplePermissions()) { result ->
        viewModel.onAndroidPermissions(result.values.all { it })
        // Asked separately so declining notifications never blocks the glasses.
        notificationPermission.launch(POST_NOTIFICATIONS)
      }

  private val notificationPermission = registerForActivityResult(RequestPermission()) {}
  private val spikePermission = registerForActivityResult(RequestPermission()) { allowed ->
    if (allowed) viewModel.runConversationSpike(::requestWearablesPermission)
    else viewModel.conversationSpikeDenied()
  }

  // Glasses permissions are granted in the Meta AI app; this bridges its result back to a suspend call.
  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val permissionMutex = Mutex()
  private val wearablesPermission =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        permissionContinuation?.resume(result.getOrDefault(PermissionStatus.Denied))
        permissionContinuation = null
      }

  private suspend fun requestWearablesPermission(permission: Permission): PermissionStatus =
      permissionMutex.withLock {
        suspendCancellableCoroutine { continuation ->
          permissionContinuation = continuation
          continuation.invokeOnCancellation { permissionContinuation = null }
          wearablesPermission.launch(permission)
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // White app: dark status and navigation bar icons.
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    setContent {
      MystApp(
          viewModel = viewModel,
          onRegister = { viewModel.gateway.startRegistration(this) },
          onUnregister = { viewModel.gateway.startUnregistration(this) },
          onCapture = { viewModel.capture(::requestWearablesPermission) },
          onWhoIsThis = { viewModel.whoIsThis(::requestWearablesPermission) },
          onConversationSpike = { spikePermission.launch(RECORD_AUDIO) },
          onMystOn = { on -> viewModel.setMystOn(on, ::requestWearablesPermission) },
          onOpenGlassesUpdate = { viewModel.openGlassesUpdate(this) },
      )
    }
  }

  override fun onStart() {
    super.onStart()
    androidPermissions.launch(arrayOf(BLUETOOTH, BLUETOOTH_CONNECT))
  }
}
