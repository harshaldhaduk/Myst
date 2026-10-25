package com.myst.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myst.core.records.Medication
import com.myst.core.records.Place
import androidx.compose.material3.FilterChip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.features.VoiceCommands
import com.myst.features.meds.BottleCheck
import com.myst.features.meds.DoseSchedule

// Caregiver setup for Lane 3: rooms and medications. Reference photos come from the glasses photo
// section above each tab, so they match what the wearer will see.

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
private fun BottleTestSection(
    ui: EnrollUiState,
    onCheckBottle: () -> Unit,
    listening: Boolean,
    onListening: (Boolean) -> Unit,
    glassesConnected: Boolean,
) {
  Section("What is this?") {
    CapabilityRow(BottleCheck.capability)
    CapabilityRow(VoiceCommands.capability)
    Row(
        Modifier.toggleable(
            value = listening,
            enabled = glassesConnected || listening,
            role = Role.Switch,
            onValueChange = onListening,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Switch(listening, onCheckedChange = null, enabled = glassesConnected || listening)
      Text(if (listening) "  Listening: say \"What is this?\"" else "  Listen for \"What is this?\"")
    }
    Text(
        "Hold one medicine close with the label toward the glasses, no faces in view. Try each " +
            "one several times, and something that is not hers: it must never say a wrong name.",
        style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onCheckBottle, enabled = !ui.checkingBottle && ui.medications.isNotEmpty()) {
      Text(if (ui.checkingBottle) "Checking…" else "What is this?")
    }
    ui.lastBottle?.let { b ->
      b.captureNote?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      if (b.captureNote == null) {
        Text(b.spoken?.let { "Spoke: \"$it\"" } ?: "Silent (guard blocked)", fontWeight = FontWeight.SemiBold)
        Text("Photo match: ${b.photoMatch}", style = MaterialTheme.typography.bodySmall)
        Text("Label read: ${b.labelRead}", style = MaterialTheme.typography.bodySmall)
        Text(b.detail, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
fun MedicationsTab(
    viewModel: EnrollViewModel,
    ui: EnrollUiState,
    onCheckBottle: () -> Unit,
    listening: Boolean,
    onListening: (Boolean) -> Unit,
    glassesConnected: Boolean,
) {
  BottleTestSection(ui, onCheckBottle, listening, onListening, glassesConnected)

  var editing by remember { mutableStateOf<Medication?>(null) }
  var labelName by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var pills by remember { mutableStateOf("1") }
  var hour by remember { mutableStateOf("8") }
  var minuteText by remember { mutableStateOf("00") }
  var pm by remember { mutableStateOf(false) }
  var window by remember { mutableStateOf("60") }
  var withFood by remember { mutableStateOf(false) }
  var withWater by remember { mutableStateOf(true) }
  var foodPlaceId by remember { mutableStateOf<String?>(null) }
  var storedInId by remember { mutableStateOf<String?>(null) }
  var storageNote by remember { mutableStateOf("") }
  var attach by remember { mutableStateOf(true) }

  fun load(m: Medication?) {
    editing = m
    labelName = m?.labelName ?: ""
    description = m?.plainDescription ?: ""
    pills = (m?.pillsPerDose ?: 1).toString()
    val dm = m?.doseMinuteOfDay ?: 8 * 60
    hour = (if (dm / 60 % 12 == 0) 12 else dm / 60 % 12).toString()
    minuteText = "%02d".format(dm % 60)
    pm = dm / 60 >= 12
    window = (m?.windowMinutes ?: 60).toString()
    withFood = m?.withFood ?: false
    withWater = m?.withWater ?: true
    foodPlaceId = m?.foodPlaceId
    storedInId = m?.storedInPlaceId
    storageNote = m?.storageNote ?: ""
  }

  val minute = toMinuteOfDay(hour, minuteText, pm)
  val pillCount = pills.toIntOrNull()
  val windowMin = window.toIntOrNull()
  val valid = labelName.isNotBlank() && minute != null && (pillCount ?: 0) >= 1 && (windowMin ?: 0) >= 1

  Section(if (editing == null) "Add a medication" else "Edit ${editing!!.labelName}") {
    OutlinedTextField(labelName, { labelName = it }, label = { Text("Name on the label (e.g. Lisinopril 10 mg)") }, singleLine = true)
    OutlinedTextField(description, { description = it }, label = { Text("In her words (e.g. the small white bottle)") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
          pills,
          { pills = it.filter(Char::isDigit) },
          label = { Text("Pills per dose") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
          window,
          { window = it.filter(Char::isDigit) },
          label = { Text("Window (min)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f),
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
          hour,
          { hour = it.filter(Char::isDigit).take(2) },
          label = { Text("Hour") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
          minuteText,
          { minuteText = it.filter(Char::isDigit).take(2) },
          label = { Text("Minute") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.weight(1f),
      )
      FilterChip(selected = !pm, onClick = { pm = false }, label = { Text("AM") })
      FilterChip(selected = pm, onClick = { pm = true }, label = { Text("PM") })
    }
    Text(
        "Myst says the pill count exactly as entered and never gives dosing advice.",
        style = MaterialTheme.typography.bodySmall,
    )

    SwitchRow("Take with water", withWater) { withWater = it }
    SwitchRow("Take with food", withFood) { withFood = it }
    if (withFood) {
      Text("Where does she get food? Myst will say \"let's go to the …\" and check she's there.")
      PlacePicker(ui.places, foodPlaceId, defaultName = "Kitchen") { foodPlaceId = it }
    }

    Text("Where is the bottle kept?", fontWeight = FontWeight.SemiBold)
    PlacePicker(ui.places, storedInId, defaultName = null) { storedInId = it }
    OutlinedTextField(storageNote, { storageNote = it }, label = { Text("Exact spot (e.g. cabinet above the sink)") }, singleLine = true)

    AttachPhotoRow(hasPhoto = ui.pending != null, attach = attach, onAttach = { attach = it })
    Text(
        "Bottle photos: hold the bottle with the label facing the glasses, close up. Add 2-3 " +
            "(front, turned a little). No faces in view.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
          onClick = {
            viewModel.saveMedication(
                Medication(
                    id = editing?.id ?: "",
                    labelName = labelName.trim(),
                    plainDescription = description.trim(),
                    pillsPerDose = pillCount ?: 1,
                    doseMinuteOfDay = minute ?: 0,
                    windowMinutes = windowMin ?: 60,
                    withFood = withFood,
                    withWater = withWater,
                    foodPlaceId = if (withFood) foodPlaceId else null,
                    storedInPlaceId = storedInId,
                    storageNote = storageNote.trim(),
                    bottlePhotoFiles = emptyList(),
                    createdAtMillis = 0,
                ),
                attach,
            )
            load(null)
          },
          enabled = valid,
      ) {
        Text(if (editing == null) "Save medication" else "Save changes")
      }
      if (editing != null) OutlinedButton(onClick = { load(null) }) { Text("Cancel") }
    }
    if (minute == null) Text("Hour 1-12 and minute 00-59.", color = MaterialTheme.colorScheme.error)
  }

  Section("Medications (${ui.medications.size})") {
    val fast by viewModel.fastDemo.collectAsStateWithLifecycle()
    CapabilityRow(
        Capability(
            "Dose reminders",
            Label.LIVE,
            "Android alarms + notification + spoken on the glasses. Re-remind at +10/+20 min, " +
                "caregiver alert when the window closes (on this phone; WhatsApp is a mock, Q13).",
        ),
    )
    SwitchRow("Fast demo timing (re-remind +1/+2 min, window 3 min)", fast) { viewModel.setFastDemo(it) }
    if (!viewModel.exactAlarmsAllowed()) {
      Text(
          "Exact alarms are off for Myst, so reminders may be a few minutes late. Turn on " +
              "Settings > Apps > Myst > Alarms & reminders.",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
    }
    if (ui.medications.isEmpty()) Text("No medications yet.")
    ui.medications.sortedBy { it.doseMinuteOfDay }.forEach { med ->
      val food = ui.places.firstOrNull { it.id == med.foodPlaceId }?.name
      val stored = ui.places.firstOrNull { it.id == med.storedInPlaceId }?.name
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
          Text(med.labelName, fontWeight = FontWeight.SemiBold)
          Text(
              "${DoseSchedule.formatTime(med.doseMinuteOfDay)} · ${med.pillsPerDose} pill${if (med.pillsPerDose == 1) "" else "s"} · " +
                  "within ${med.windowMinutes} min",
              style = MaterialTheme.typography.bodySmall,
          )
          Text(
              listOfNotNull(
                      if (med.withWater) "with water" else null,
                      if (med.withFood) "with food${food?.let { " (go to the ${it.lowercase()})" } ?: ""}" else null,
                      stored?.let { "kept in the ${it.lowercase()}" + med.storageNote.takeIf(String::isNotBlank)?.let { n -> ", $n" }.orEmpty() },
                  )
                  .joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
          )
          Text("${med.bottlePhotoFiles.size} bottle photo(s)", style = MaterialTheme.typography.bodySmall)
          val dose = DoseSchedule.currentDoseTime(med, System.currentTimeMillis())
          val taken = DoseSchedule.reportedTaken(med, dose, ui.events)
          Text(
              taken?.let { "Reported taken at ${DoseSchedule.formatTimeOfDay(it.atMillis)}" }
                  ?: "Next: ${DoseSchedule.formatTimeOfDay(dose)}, not reported taken",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(onClick = { load(med) }) { Text("Edit") }
          OutlinedButton(onClick = { viewModel.deleteMedication(med) }) { Text("Delete") }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.remindNow(med) }) { Text("Remind now (demo)") }
        OutlinedButton(onClick = { viewModel.markTaken(med) }) { Text("Reported taken") }
      }
      PhotoStrip(med.bottlePhotoFiles, viewModel)
    }
  }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Switch(checked, onCheckedChange = null)
    Text("  $label")
  }
}

@Composable
private fun AttachPhotoRow(hasPhoto: Boolean, attach: Boolean, onAttach: (Boolean) -> Unit) {
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
private fun PlacePicker(places: List<Place>, selected: String?, defaultName: String?, onSelect: (String?) -> Unit) {
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
private fun PhotoStrip(files: List<String>, viewModel: EnrollViewModel) {
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
