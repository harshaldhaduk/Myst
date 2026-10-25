package com.myst

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.ui.EnrollScreen
import com.myst.ui.EnrollViewModel
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
    enableEdgeToEdge()
    setContent {
      EnrollScreen(
          viewModel = viewModel,
          onRegister = { viewModel.gateway.startRegistration(this) },
          onUnregister = { viewModel.gateway.startUnregistration(this) },
          onCapture = { viewModel.capture(::requestWearablesPermission) },
          onWhoIsThis = { viewModel.whoIsThis(::requestWearablesPermission) },
          onListening = { on -> viewModel.setListening(on, ::requestWearablesPermission) },
          onOpenGlassesUpdate = { viewModel.openGlassesUpdate(this) },
          onCheckBottle = { viewModel.checkBottle(::requestWearablesPermission) },
          onTapTrigger = { on -> viewModel.setTapTrigger(on, ::requestWearablesPermission) },
      )
    }
  }

  override fun onStart() {
    super.onStart()
    androidPermissions.launch(arrayOf(BLUETOOTH, BLUETOOTH_CONNECT))
  }
}
