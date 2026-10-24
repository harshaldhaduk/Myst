package com.myst.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.records.Person
import com.myst.core.records.PhotoSource
import com.myst.glasses.GlassesState

@Composable
fun EnrollScreen(
    viewModel: EnrollViewModel,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onCapture: () -> Unit,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val glasses by viewModel.gateway.state.collectAsStateWithLifecycle()
  val camera by viewModel.gateway.capability.collectAsStateWithLifecycle()
  var mockOn by rememberSaveable { mutableStateOf(false) }

  MaterialTheme {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Myst · family setup", style = MaterialTheme.typography.headlineSmall)
      ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

      Section("1. Connect the glasses") {
        Text("Status: ${glasses.describe()}")
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
              checked = mockOn,
              enabled = ui.datReady,
              onCheckedChange = {
                mockOn = it
                viewModel.setMockGlasses(it)
              },
          )
          Text("  Use simulated glasses (no hardware)  ", Modifier.weight(1f))
          LabelChip(Label.MOCK)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onRegister, enabled = ui.datReady && !mockOn) {
            Text("Register with Meta AI app")
          }
          OutlinedButton(onClick = onUnregister, enabled = ui.datReady && !mockOn) {
            Text("Unregister")
          }
        }
      }

      Section("2. Take a photo with the glasses") {
        CapabilityRow(camera)
        Button(
            onClick = onCapture,
            enabled = glasses is GlassesState.Connected && !ui.capturing,
        ) {
          Text(if (ui.capturing) "Capturing…" else "Take photo")
        }
        ui.pending?.let { pending ->
          JpegImage(pending.photo.jpeg, Modifier.fillMaxWidth().height(280.dp))
          val t = pending.timings
          Text(
              "${pending.photo.width}×${pending.photo.height} · total ${t.totalMs} ms " +
                  "(permission ${t.permissionMs}, session ${t.sessionMs}, stream ${t.streamMs}, " +
                  "capture ${t.captureMs})",
              style = MaterialTheme.typography.bodySmall,
          )
          if (pending.source == PhotoSource.MOCK_DEVICE_KIT) {
            Text("Mock photo: placeholder from the Mock Device Kit, not a real capture.")
          }
          OutlinedButton(onClick = viewModel::discardPhoto) { Text("Discard photo") }
        }
      }

      Section("3. Name the person and save") {
        EnrollForm(
            hasPhoto = ui.pending != null,
            onSave = { name, relationship, recordedBy ->
              viewModel.enroll(name, relationship, recordedBy)
            },
        )
        Text(
            "Saved in Myst's private storage on this phone only. Not uploaded, not backed up, " +
                "and never sent to Muse.",
            style = MaterialTheme.typography.bodySmall,
        )
      }

      Section("Enrolled family (${ui.people.size})") {
        if (ui.people.isEmpty()) Text("Nobody yet.")
        ui.people.forEach { person ->
          PersonRow(person, viewModel.photoBytes(person)) { viewModel.revoke(person) }
        }
      }

      Section("Recognition") {
        CapabilityRow(
            Capability(
                "Face identification",
                Label.MOCK,
                "Off. Enrollment only; no matching runs until Meta approves (D7) and Q12 is answered.",
                blockedBy = "D7, Q2, Q12",
            ),
        )
      }
    }
  }
}

@Composable
private fun EnrollForm(hasPhoto: Boolean, onSave: (String, String, String) -> Unit) {
  var name by remember { mutableStateOf("") }
  var relationship by remember { mutableStateOf("") }
  var recordedBy by remember { mutableStateOf("") }
  var consent by remember { mutableStateOf(false) }

  OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
  OutlinedTextField(
      relationship,
      { relationship = it },
      label = { Text("Relationship (e.g. son)") },
      singleLine = true,
  )
  OutlinedTextField(
      recordedBy,
      { recordedBy = it },
      label = { Text("Consent recorded by (caregiver)") },
      singleLine = true,
  )
  Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(consent, { consent = it })
    Text("${name.ifBlank { "This person" }} agreed to be recognized by Myst")
  }
  if (!hasPhoto) Text("No photo attached. You can save now and add one later.")
  Button(
      onClick = {
        onSave(name, relationship, recordedBy)
        name = ""
        relationship = ""
        recordedBy = ""
        consent = false
      },
      enabled = consent && name.isNotBlank() && recordedBy.isNotBlank(),
  ) {
    Text("Save")
  }
}

@Composable
private fun PersonRow(person: Person, photo: ByteArray?, onRevoke: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    if (photo != null) JpegImage(photo, Modifier.size(56.dp))
    Column(Modifier.weight(1f)) {
      Text(person.name, fontWeight = FontWeight.SemiBold)
      Text(person.relationship.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
      Text("Consent recorded by ${person.consent.recordedBy}", style = MaterialTheme.typography.bodySmall)
      if (person.photoSource == PhotoSource.MOCK_DEVICE_KIT) LabelChip(Label.MOCK)
    }
    OutlinedButton(onClick = onRevoke) { Text("Revoke") }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      content()
    }
  }
}

@Composable
private fun CapabilityRow(c: Capability) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    LabelChip(c.label)
    Column {
      Text(c.name, fontWeight = FontWeight.SemiBold)
      Text(c.note, style = MaterialTheme.typography.bodySmall)
      c.blockedBy?.let { Text("Blocked by $it", style = MaterialTheme.typography.bodySmall) }
    }
  }
}

@Composable
fun LabelChip(label: Label) {
  val color =
      when (label) {
        Label.LIVE -> Color(0xFF1B7F3B)
        Label.MOCK -> Color(0xFFB3261E)
        Label.EXPERIMENTAL -> Color(0xFF8A5A00)
      }
  Text(
      label.name,
      color = Color.White,
      style = MaterialTheme.typography.labelSmall,
      modifier =
          Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

@Composable
private fun JpegImage(bytes: ByteArray, modifier: Modifier) {
  val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return
  Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

private fun GlassesState.describe(): String =
    when (this) {
      GlassesState.NotInitialized -> "waiting for Bluetooth permission"
      GlassesState.Unregistered -> "not registered with the Meta AI app"
      GlassesState.Registering -> "registering in the Meta AI app…"
      GlassesState.Disconnected -> "registered, no glasses connected"
      is GlassesState.Connected -> "connected ($deviceCount device${if (deviceCount == 1) "" else "s"})"
      GlassesState.Capturing -> "capturing…"
    }
