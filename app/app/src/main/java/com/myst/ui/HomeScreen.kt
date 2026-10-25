package com.myst.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.features.VoiceCommands
import com.myst.features.meds.BottleCheck
import com.myst.features.meds.DoseSchedule
import com.myst.glasses.GlassesState
import com.myst.glasses.GlassesTapTrigger
import com.myst.ui.theme.Myst
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: EnrollViewModel,
    ui: EnrollUiState,
    glasses: GlassesState,
    mystOn: Boolean,
    onMystOn: (Boolean) -> Unit,
    onWhoIsThis: () -> Unit,
    onWhatIsThis: () -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onOpenGlassesUpdate: () -> Unit,
    camera: Capability,
) {
  val connected = glasses is GlassesState.Connected
  val busy = ui.identifying || ui.capturing || ui.checkingBottle
  val faceMatch = viewModel.museIdentity != null
  val museKeySet by viewModel.museKeySet.collectAsStateWithLifecycle()

  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Myst", style = MaterialTheme.typography.displaySmall)
        Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = Myst.InkSoft)
      }
      GlassesPill(glasses)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Orb(on = mystOn, enabled = connected || mystOn, onToggle = onMystOn)
      Spacer(Modifier.height(18.dp))
      Text(
          when {
            mystOn -> "Myst is on"
            connected -> "Tap to start Myst"
            else -> "Connect the glasses to start"
          },
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
      )
      Text(
          if (mystOn) {
            "Say \"Who is this?\" or \"What is this?\", or tap the glasses."
          } else {
            "One switch for voice and glasses tap."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = Myst.InkSoft,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 24.dp),
      )
      if (mystOn && ui.voiceProblem != null) {
        Spacer(Modifier.height(14.dp))
        Panel(tint = Myst.BlushWash, radius = 22.dp, padding = 16.dp) {
          Text(ui.voiceProblem, style = MaterialTheme.typography.bodyMedium)
          Text("Tapping the glasses still works.", style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
          PillButton("Try voice again", { onMystOn(true) }, color = Myst.Blush)
        }
      }
      if (!faceMatch) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Face recognition isn't set up on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Myst.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
      }
      if (museKeySet == false) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Muse key not set — run scripts/provision-muse-key.sh",
            style = MaterialTheme.typography.bodySmall,
            color = Myst.Rose,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
      }
      if (mystOn && faceMatch) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Face recognition is a test feature: each photo goes to Meta's Muse. Use it only " +
                "around family who agreed.",
            style = MaterialTheme.typography.bodySmall,
            color = Myst.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      PillButton(
          if (ui.identifying) "Looking…" else "Who is this?",
          onWhoIsThis,
          Modifier.weight(1f),
          color = Myst.Blush,
          enabled = connected && !busy,
      )
      PillButton(
          if (ui.checkingBottle) "Looking…" else "What is this?",
          onWhatIsThis,
          Modifier.weight(1f),
          color = Myst.Periwinkle,
          enabled = connected && !busy && ui.medications.isNotEmpty(),
      )
    }

    LastSaidCard(ui.lastSaid)

    GlassesCard(ui, glasses, onRegister, onUnregister, onOpenGlassesUpdate)

    if (ui.medications.isNotEmpty() && !viewModel.exactAlarmsAllowed()) {
      Panel(tint = Myst.BlushWash, radius = 22.dp, padding = 16.dp) {
        Text("Reminders may run a few minutes late", style = MaterialTheme.typography.titleMedium)
        Text(
            "Turn on Settings > Apps > Myst > Alarms & reminders so dose reminders arrive on time.",
            style = MaterialTheme.typography.bodySmall,
        )
      }
    }

    AboutCard(viewModel, camera)
  }
}

/** The one switch. A soft gradient that breathes while Myst is listening. */
@Composable
private fun Orb(on: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
  val context = LocalContext.current
  val reduceMotion =
      remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
      }
  val breath = rememberInfiniteTransition(label = "breath")
  val pulse by
      breath.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
          label = "pulse",
      )
  val p = if (on && !reduceMotion) pulse else 0f
  val ring by animateColorAsState(if (on) Myst.Lilac else Myst.Line, label = "ring")

  Box(
      Modifier.size(232.dp)
          .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
          .drawBehind {
            val r = size.minDimension / 2
            if (on) {
              // Halo: grows and fades with each breath.
              drawCircle(
                  Brush.radialGradient(
                      listOf(Myst.Blush.copy(alpha = 0.55f - 0.25f * p), Color.Transparent),
                      center = center,
                      radius = r,
                  ),
                  radius = r,
              )
            }
            drawCircle(ring, radius = r * 0.78f, style = Stroke(width = 2.dp.toPx()))
          },
      contentAlignment = Alignment.Center,
  ) {
    Box(
        Modifier.size(150.dp)
            .scale(1f + 0.05f * p)
            .drawBehind {
              val colors =
                  if (on) listOf(Myst.Periwinkle, Myst.Lilac, Myst.Blush)
                  else listOf(Myst.Periwinkle, Myst.Lilac, Myst.Blush).map { it.copy(alpha = 0.45f) }
              drawCircle(
                  Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(size.width, size.height)),
              )
            },
        contentAlignment = Alignment.Center,
    ) {
      Text(
          if (on) "On" else "Start",
          style = MaterialTheme.typography.titleLarge,
          color = if (enabled || on) Myst.Ink else Myst.InkSoft,
      )
    }
  }
}

@Composable
private fun GlassesPill(glasses: GlassesState) {
  val (text, dot) =
      when (glasses) {
        is GlassesState.Connected -> "Glasses on" to Myst.Leaf
        GlassesState.Capturing -> "Taking photo" to Myst.LilacStrong
        GlassesState.Registering -> "Connecting" to Myst.LilacStrong
        GlassesState.Disconnected -> "Not connected" to Myst.Blush
        GlassesState.Unregistered -> "Not set up" to Myst.Blush
        GlassesState.NotInitialized -> "Starting" to Myst.Line
      }
  Row(
      Modifier.background(Myst.Mist, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(9.dp).background(dot, CircleShape))
    Text(text, style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
private fun LastSaidCard(said: LastSaid?) {
  Panel(tint = Myst.LilacWash) {
    SmallLabel("Last thing Myst said")
    if (said == null) {
      Text(
          "Nothing yet. Ask \"Who is this?\" and the answer shows up here.",
          style = MaterialTheme.typography.bodyMedium,
          color = Myst.InkSoft,
      )
    } else {
      Text(
          if (said.quiet) said.text else "“${said.text}”",
          style = if (said.quiet) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
          color = if (said.quiet) Myst.InkSoft else Myst.Ink,
      )
      Text(DoseSchedule.formatTimeOfDay(said.atMillis), style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
      said.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Myst.Rose) }
    }
  }
}

@Composable
private fun GlassesCard(
    ui: EnrollUiState,
    glasses: GlassesState,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onOpenGlassesUpdate: () -> Unit,
) {
  Panel(tint = Myst.Mist) {
    Text("Glasses", style = MaterialTheme.typography.titleLarge)
    Text(
        when (glasses) {
          GlassesState.NotInitialized -> "Waiting for Bluetooth permission."
          GlassesState.Unregistered -> "Connect Myst to your Ray-Ban Meta glasses through the Meta AI app."
          GlassesState.Registering -> "Finish connecting in the Meta AI app."
          GlassesState.Disconnected -> "Myst is set up. Put the glasses on and make sure they're connected in the Meta AI app."
          is GlassesState.Connected ->
              "Connected to ${glasses.deviceCount} pair${if (glasses.deviceCount == 1) "" else "s"} of glasses."
          GlassesState.Capturing -> "Taking a photo…"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (ui.needsGlassesUpdate) {
      PillButton("Update the glasses in Meta AI", onOpenGlassesUpdate, color = Myst.Blush)
    }
    when (glasses) {
      GlassesState.Unregistered -> PillButton("Connect glasses", onRegister, enabled = ui.datReady)
      GlassesState.NotInitialized, GlassesState.Registering -> Unit
      else -> QuietButton("Disconnect Myst from the glasses", onUnregister, enabled = ui.datReady)
    }
  }
}

/** What is real on this phone and what is not (AGENTS.md: label live, mock and experimental). */
@Composable
private fun AboutCard(viewModel: EnrollViewModel, camera: Capability) {
  var open by rememberSaveable { mutableStateOf(false) }
  Panel(tint = Myst.White, radius = 22.dp, padding = 4.dp) {
    QuietButton(if (open) "Hide how Myst works" else "How Myst works on this phone", { open = !open }, color = Myst.Ink)
    AnimatedVisibility(open) {
      Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CapabilityRow(camera)
        CapabilityRow(viewModel.museIdentity?.capability ?: FACE_ID_NOT_IN_BUILD)
        CapabilityRow(viewModel.speaker.capability)
        CapabilityRow(VoiceCommands.capability)
        CapabilityRow(GlassesTapTrigger.capability)
        CapabilityRow(BottleCheck.capability)
        Text(
            "Family records, memories and photos stay in Myst's private storage on this phone. " +
                "They are not backed up.",
            style = MaterialTheme.typography.bodySmall,
            color = Myst.InkSoft,
        )
      }
    }
  }
}

private val FACE_ID_NOT_IN_BUILD =
    Capability(
        "Face recognition",
        Label.MOCK,
        "Not in this build: no photo is taken and Myst answers \"Face recognition isn't set up on this phone.\"",
        blockedBy = "myst.allowFaceId=true in local.properties (D12)",
    )

private fun greeting(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
      in 5..11 -> "Good morning"
      in 12..16 -> "Good afternoon"
      else -> "Good evening"
    }
