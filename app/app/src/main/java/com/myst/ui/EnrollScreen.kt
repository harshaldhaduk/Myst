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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.myst.core.records.EventType
import com.myst.core.records.PhotoSource
import com.myst.core.records.VisitMemory
import com.myst.features.VoiceCommands
import com.myst.glasses.GlassesState

@Composable
fun EnrollScreen(
    viewModel: EnrollViewModel,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onCapture: () -> Unit,
    onWhoIsThis: () -> Unit,
    onListening: (Boolean) -> Unit,
    onOpenGlassesUpdate: () -> Unit,
    onCheckBottle: () -> Unit,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val listening by viewModel.gateway.isListening.collectAsStateWithLifecycle()
  val autoIdentify by viewModel.autoIdentify.collectAsStateWithLifecycle()
  val glasses by viewModel.gateway.state.collectAsStateWithLifecycle()
  val camera by viewModel.gateway.capability.collectAsStateWithLifecycle()
  var mockOn by rememberSaveable { mutableStateOf(false) }
  var tab by rememberSaveable { mutableStateOf(0) }

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
      if (ui.needsGlassesUpdate) {
        Button(onClick = onOpenGlassesUpdate) { Text("Open glasses update (Meta AI app)") }
      }
      TabRow(selectedTabIndex = tab) {
        listOf("Family", "Medications", "Places").forEachIndexed { i, title ->
          Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
        }
      }
      if (tab != 0) Text("Glasses: ${glasses.describe()}", style = MaterialTheme.typography.bodySmall)

      if (tab == 0) Section("1. Connect the glasses") {
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

      Section(
          when (tab) {
            1 -> "Bottle photo (glasses)"
            2 -> "Room photo (glasses, no people in view)"
            else -> "2. Take a photo with the glasses"
          },
      ) {
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

      if (tab == 1) {
        MedicationsTab(viewModel, ui, onCheckBottle, listening, onListening, glasses is GlassesState.Connected)
      }
      if (tab == 2) {
        PlacesTab(viewModel, ui)
      }

      if (tab == 0) Section("3. Name the person and save") {
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

      if (tab == 0) Section("Enrolled family (${ui.people.size})") {
        if (ui.people.isEmpty()) Text("Nobody yet.")
        ui.people.forEach { person ->
          val last = ui.events.filter { it.type == EventType.VISIT && it.personId == person.id }.maxOfOrNull { it.atMillis }
          val now = System.currentTimeMillis()
          PersonRow(
              person,
              viewModel.photoBytes(person),
              lastSeen =
                  when {
                    last == null -> "Not recognized yet"
                    else -> "Last recognized: " + (VisitMemory.whenPhrase(last, now) ?: "more than a week ago")
                  },
              onBackfill = { viewModel.backfillSighting(person, 5) },
              onRevoke = { viewModel.revoke(person) },
              onAddMemory = { viewModel.addMemory(person, it) },
              onRemoveMemory = { viewModel.removeMemory(person, it) },
              onMakeFirst = { viewModel.makeFirstMemory(person, it) },
          )
        }
      }

      if (tab == 0) Section("4. Who is this?") {
        viewModel.museIdentity?.let { muse ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = autoIdentify, onCheckedChange = viewModel::setAutoIdentify)
            Text("  Recognize automatically (Muse face match)", Modifier.weight(1f))
          }
          if (autoIdentify) {
            CapabilityRow(muse.capability)
            Text(
                "UNAPPROVED, dev only (D12). Use only when every face in view belongs to a " +
                    "consenting teammate: the whole photo is sent to Meta's Model API.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        if (!autoIdentify) CapabilityRow(viewModel.mockIdentity.capability)
        CapabilityRow(viewModel.speaker.capability)
        if (!autoIdentify) {
          Text("Demo Controls: who is in front of the wearer?", fontWeight = FontWeight.SemiBold)
          Row(
              Modifier.selectable(ui.mockPickId == null, role = Role.RadioButton) {
                viewModel.pickMockPerson(null)
              },
              verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(ui.mockPickId == null, onClick = null)
            Text("Someone not enrolled")
          }
          ui.people.forEach { person ->
            Row(
                Modifier.selectable(ui.mockPickId == person.id, role = Role.RadioButton) {
                  viewModel.pickMockPerson(person.id)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(ui.mockPickId == person.id, onClick = null)
              Text("${person.name} (${person.relationship.ifBlank { "—" }})")
            }
          }
        }
        OutlinedButton(onClick = viewModel::loadDemoData) { Text("Load demo data (fictional)") }
        Text(
            "DEMO: adds Daniel (son) and Emily (granddaughter) with one memory and an earlier sighting each. " +
                "No photos, so recognize them with the mock identity picker above.",
            style = MaterialTheme.typography.bodySmall,
        )
        CapabilityRow(VoiceCommands.capability)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
              checked = listening,
              enabled = glasses is GlassesState.Connected || listening,
              onCheckedChange = onListening,
          )
          Text(
              if (listening) "  Listening: say \"Who is this?\" or \"What is this?\""
              else "  Listen for \"Who is this?\" and \"What is this?\"",
              Modifier.weight(1f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
              onClick = onWhoIsThis,
              enabled = glasses is GlassesState.Connected && !ui.identifying && !ui.capturing,
          ) {
            Text(if (ui.identifying) "Working…" else "Who is this?")
          }
          if (mockOn && listening) {
            OutlinedButton(onClick = viewModel::simulateWhoIsThis) { Text("Simulate saying it") }
          }
        }
        ui.lastWhisper?.let { w ->
          Text(
              w.spoken?.let { "Spoke: \"$it\"" } ?: "Silent: guard blocked (${w.blockedReason})",
              fontWeight = FontWeight.SemiBold,
          )
          Text(
              "Identity: ${w.identityLabel} · glasses photo ${w.captureMs?.let { "$it ms, then discarded" } ?: "none"}" +
                  " · trigger to speech ${w.totalMs} ms",
              style = MaterialTheme.typography.bodySmall,
          )
          w.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
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
      label = { Text("Caregiver's name (who recorded consent)") },
      singleLine = true,
  )
  Row(
      Modifier.toggleable(value = consent, role = Role.Checkbox, onValueChange = { consent = it }),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(consent, onCheckedChange = null)
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
private fun PersonRow(
    person: Person,
    photo: ByteArray?,
    lastSeen: String,
    onBackfill: () -> Unit,
    onRevoke: () -> Unit,
    onAddMemory: (String) -> Unit,
    onRemoveMemory: (Int) -> Unit,
    onMakeFirst: (Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      if (photo != null) JpegImage(photo, Modifier.size(56.dp))
      Column(Modifier.weight(1f)) {
        Text(person.name, fontWeight = FontWeight.SemiBold)
        Text(person.relationship.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
        Text("Consent recorded by ${person.consent.recordedBy}", style = MaterialTheme.typography.bodySmall)
        Text(lastSeen, style = MaterialTheme.typography.bodySmall)
        if (person.photoSource == PhotoSource.MOCK_DEVICE_KIT) LabelChip(Label.MOCK)
      }
      Column(horizontalAlignment = Alignment.End) {
        OutlinedButton(onClick = onRevoke) { Text("Revoke") }
        TextButton(onClick = onBackfill) { Text("Demo: seen 5 h ago") }
      }
    }
    Text(
        if (person.memories.isEmpty()) "No memories yet. Add one the wearer will hear after the name."
        else "Memories (the first one is spoken):",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
    person.memories.forEachIndexed { i, m ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${i + 1}. ${m.text}  (by ${m.author})",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        if (i > 0) TextButton(onClick = { onMakeFirst(i) }) { Text("Use first") }
        TextButton(onClick = { onRemoveMemory(i) }) { Text("Delete") }
      }
    }
    var draft by remember(person.id) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
          draft,
          { draft = it },
          Modifier.weight(1f),
          label = { Text("New memory, e.g. You both love the park") },
          singleLine = true,
      )
      Button(onClick = { onAddMemory(draft); draft = "" }, enabled = draft.isNotBlank()) { Text("Add") }
    }
  }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      content()
    }
  }
}

@Composable
internal fun CapabilityRow(c: Capability) {
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
internal fun JpegImage(bytes: ByteArray, modifier: Modifier) {
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
