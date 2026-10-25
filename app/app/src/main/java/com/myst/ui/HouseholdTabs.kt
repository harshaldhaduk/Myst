package com.myst.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myst.core.records.Place

// Caregiver setup for Lane 3: rooms. Reference photos come from the glasses photo card above the
// Places list, so they match what the wearer will see. Medicine setup lives in SetupScreen.kt.

@Composable
fun PlacesTab(viewModel: EnrollViewModel, ui: EnrollUiState) {
  var editing by remember { mutableStateOf<Place?>(null) }
  var name by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var attach by remember { mutableStateOf(true) }

  Section(if (editing == null) "Add a place" else "Edit ${editing!!.name}") {
    OutlinedTextField(name, { name = it }, label = { Text("Room name (e.g. Kitchen)") }, singleLine = true)
    OutlinedTextField(
        note,
        { note = it },
        label = { Text("What's there (e.g. the crackers are in the pantry)") },
    )
    AttachPhotoRow(hasPhoto = ui.pending != null, attach = attach, onAttach = { attach = it })
    Text(
        "Take 2-3 photos from where she usually stands, with nobody in view: photos go to Muse " +
            "to recognize the room (Q12).",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
          onClick = {
            viewModel.savePlace(editing?.id, name, note, attach)
            editing = null
            name = ""
            note = ""
          },
          enabled = name.isNotBlank(),
      ) {
        Text(if (editing == null) "Save place" else "Save changes")
      }
      if (editing != null) {
        OutlinedButton(
            onClick = {
              editing = null
              name = ""
              note = ""
            },
        ) {
          Text("Cancel")
        }
      }
    }
  }

  Section("Places (${ui.places.size})") {
    if (ui.places.isEmpty()) Text("No places yet. Start with the kitchen.")
    ui.places.forEach { place ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
          Text(place.name, fontWeight = FontWeight.SemiBold)
          if (place.note.isNotBlank()) Text(place.note, style = MaterialTheme.typography.bodySmall)
          Text("${place.photoFiles.size} reference photo(s)", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = {
              editing = place
              name = place.name
              note = place.note
            },
        ) {
          Text("Edit")
        }
        OutlinedButton(onClick = { viewModel.deletePlace(place) }) { Text("Delete") }
      }
      PhotoStrip(place.photoFiles, viewModel)
    }
  }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Switch(checked, onCheckedChange = null)
    Text("  $label")
  }
}

@Composable
internal fun AttachPhotoRow(hasPhoto: Boolean, attach: Boolean, onAttach: (Boolean) -> Unit) {
  if (!hasPhoto) {
    Text("No photo taken. Use Take photo above to add a reference photo.", style = MaterialTheme.typography.bodySmall)
    return
  }
  Row(
      Modifier.toggleable(value = attach, role = Role.Checkbox, onValueChange = onAttach),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(attach, onCheckedChange = null)
    Text("Add the photo above as a reference")
  }
}

/** Choose one of the caregiver's places; [defaultName] is preselected when nothing is chosen yet. */
@Composable
internal fun PlacePicker(places: List<Place>, selected: String?, defaultName: String?, onSelect: (String?) -> Unit) {
  if (places.isEmpty()) {
    Text("Add places in the Places tab first.", style = MaterialTheme.typography.bodySmall)
    return
  }
  val effective =
      selected ?: defaultName?.let { d -> places.firstOrNull { it.name.equals(d, ignoreCase = true) }?.id }
  LaunchedEffect(effective) { if (effective != selected && effective != null) onSelect(effective) }
  places.forEach { place ->
    Row(
        Modifier.selectable(effective == place.id, role = Role.RadioButton) { onSelect(place.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(effective == place.id, onClick = null)
      Text(place.name)
    }
  }
}

@Composable
internal fun PhotoStrip(files: List<String>, viewModel: EnrollViewModel) {
  if (files.isEmpty()) return
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    files.take(4).forEach { f -> viewModel.householdPhoto(f)?.let { JpegImage(it, Modifier.size(56.dp)) } }
  }
}

/** 12-hour entry to minutes after midnight; 12 AM is midnight, 12 PM is noon. */
internal fun toMinuteOfDay(hour: String, minute: String, pm: Boolean): Int? {
  val h = hour.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
  val m = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
  return ((h % 12) + if (pm) 12 else 0) * 60 + m
}
