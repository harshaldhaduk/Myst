package com.myst.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myst.glasses.GlassesState
import com.myst.ui.theme.Myst
import com.myst.ui.theme.MystTheme
import kotlinx.coroutines.delay

/** Two tabs: Home (glasses, the one switch, try it) and Setup (family, medicine, places). */
@Composable
fun MystApp(
    viewModel: EnrollViewModel,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onCapture: () -> Unit,
    onWhoIsThis: () -> Unit,
    onMystOn: (Boolean) -> Unit,
    onOpenGlassesUpdate: () -> Unit,
    onCheckBottle: () -> Unit,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val glasses by viewModel.gateway.state.collectAsStateWithLifecycle()
  val camera by viewModel.gateway.capability.collectAsStateWithLifecycle()
  val mystOn by viewModel.mystOn.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableStateOf(0) }

  // Messages are short confirmations; they step aside after a few seconds.
  LaunchedEffect(ui.message) {
    if (ui.message != null) {
      delay(4_000)
      viewModel.clearMessage()
    }
  }

  MystTheme {
    Surface(Modifier.fillMaxSize(), color = Myst.White, contentColor = Myst.Ink) {
    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                // Insets outside the scroll, so content never slides under the status bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
          when (tab) {
            0 ->
                HomeScreen(
                    viewModel,
                    ui,
                    glasses,
                    mystOn,
                    onMystOn,
                    onWhoIsThis,
                    onCheckBottle,
                    onRegister,
                    onUnregister,
                    onOpenGlassesUpdate,
                    camera,
                )
            else -> SetupScreen(viewModel, ui, glasses is GlassesState.Connected, onCapture)
          }
        }
        BottomBar(tab) { tab = it }
      }

      AnimatedVisibility(
          ui.message != null,
          Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
          enter = fadeIn() + slideInVertically { -it },
          exit = fadeOut() + slideOutVertically { -it },
      ) {
        Text(
            ui.message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Myst.Ink,
            modifier =
                Modifier.fillMaxWidth()
                    .background(Myst.Lilac, MaterialTheme.shapes.large)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
        )
      }
    }
    }
  }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
  Row(
      Modifier.fillMaxWidth()
          .background(Myst.White)
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 10.dp)
          .background(Myst.Mist, CircleShape)
          .padding(5.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    listOf("Home", "Setup").forEachIndexed { i, title ->
      val selected = tab == i
      Box(
          Modifier.weight(1f)
              .clip(CircleShape)
              .background(if (selected) Myst.Lilac else Color.Transparent)
              .selectable(selected, role = Role.Tab) { onTab(i) }
              .padding(vertical = 13.dp),
          contentAlignment = Alignment.Center,
      ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) Myst.Ink else Myst.InkSoft)
      }
    }
  }
}
