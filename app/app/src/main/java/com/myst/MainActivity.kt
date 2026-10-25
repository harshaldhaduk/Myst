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
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.ui.EnrollViewModel
import com.myst.ui.MystApp
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
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
  private val conversationPermission = registerForActivityResult(RequestPermission()) { allowed ->
    if (allowed) viewModel.enableLiveConversation() else viewModel.liveConversationPermissionDenied()
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

  private suspend fun checkWearablesPermission(permission: Permission): PermissionStatus =
      Wearables.checkPermissionStatus(permission).getOrDefault(PermissionStatus.Denied)

  private suspend fun requestMystPermissions() {
    // DAT grants each capability in Meta AI. Ask for both from the Start Myst tap so a later
    // spoken face request never sends the wearer to a permission screen.
    for (permission in listOf(Permission.MICROPHONE, Permission.CAMERA)) {
      if (checkWearablesPermission(permission) != PermissionStatus.Granted) {
        requestWearablesPermission(permission)
      }
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
          onEnableLiveConversation = { conversationPermission.launch(RECORD_AUDIO) },
          onStartLiveConversation = { viewModel.startLiveConversation(::requestWearablesPermission) },
          onMystOn = { on ->
            if (on) {
              lifecycleScope.launch {
                requestMystPermissions()
                viewModel.setMystOn(true, ::checkWearablesPermission)
              }
            } else {
              viewModel.setMystOn(false, ::checkWearablesPermission)
            }
          },
          onOpenGlassesUpdate = { viewModel.openGlassesUpdate(this) },
      )
    }
  }

  override fun onStart() {
    super.onStart()
    androidPermissions.launch(arrayOf(BLUETOOTH, BLUETOOTH_CONNECT))
  }
}
