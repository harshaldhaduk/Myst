package com.myst.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myst.core.records.EventType
import com.myst.core.records.Medication
import com.myst.core.records.Person
import com.myst.core.records.VisitMemory
import com.myst.features.meds.DoseSchedule
import com.myst.ui.theme.Myst

private enum class SetupPart(val title: String, val tint: Color) {
  FAMILY("Family", Myst.Blush),
  MEDICINE("Medicine", Myst.Periwinkle),
  PLACES("Places", Myst.Lilac),
}

@Composable
fun SetupScreen(
    viewModel: EnrollViewModel,
    ui: EnrollUiState,
    connected: Boolean,
    onCapture: () -> Unit,
) {
  var part by rememberSaveable { mutableStateOf(SetupPart.FAMILY) }

  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Column {
      Text("Setup", style = MaterialTheme.typography.displaySmall)
      Text(
          "The people and medicines Myst helps with.",
          style = MaterialTheme.typography.bodyMedium,
          color = Myst.InkSoft,
      )
    }
    Row(
        Modifier.fillMaxWidth().background(Myst.Mist, CircleShape).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      SetupPart.entries.forEach { p ->
        val selected = p == part
        Box(
            Modifier.weight(1f)
                .clip(CircleShape)
                .background(if (selected) p.tint else Color.Transparent)
                .selectable(selected, role = Role.Tab) { part = p }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
          Text(p.title, style = MaterialTheme.typography.labelLarge, color = if (selected) Myst.Ink else Myst.InkSoft)
        }
      }
    }
    when (part) {
      SetupPart.FAMILY -> FamilyPart(viewModel, ui, connected, onCapture)
      SetupPart.MEDICINE -> MedicinePart(viewModel, ui, connected, onCapture)
      SetupPart.PLACES -> {
        GlassesPhotoCard(
            ui,
            connected,
            "Room photos help Myst guide her to the right place. Nobody should be in view.",
            Myst.LilacWash,
            onCapture,
            viewModel::discardPhoto,
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { PlacesTab(viewModel, ui) }
      }
    }
  }
}

// MARK: - Family

@Composable
private fun FamilyPart(viewModel: EnrollViewModel, ui: EnrollUiState, connected: Boolean, onCapture: () -> Unit) {
  var adding by rememberSaveable { mutableStateOf(false) }

  if (!adding) {
    PillButton("Add someone", { adding = true }, Modifier.fillMaxWidth(), color = Myst.Blush)
  }
  AnimatedVisibility(adding) {
    AddPersonCard(
        ui,
        connected,
        onCapture,
        onDiscard = viewModel::discardPhoto,
        onSave = { name, relationship, recordedBy ->
          viewModel.enroll(name, relationship, recordedBy)
          adding = false
        },
        onCancel = { adding = false },
    )
  }

  if (ui.people.isEmpty() && !adding) {
    Panel(tint = Myst.BlushWash) {
      Text("No one yet", style = MaterialTheme.typography.titleLarge)
      Text(
          "Add the people she sees most. Each person agrees first, and you can remove them any time.",
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
  val now = System.currentTimeMillis()
  ui.people.forEach { person ->
    val last = ui.events.filter { it.type == EventType.VISIT && it.personId == person.id }.maxOfOrNull { it.atMillis }
    PersonCard(
        person,
        viewModel.photoBytes(person),
        lastSeen =
            if (last == null) "Not recognized yet"
            else "Last recognized " + (VisitMemory.whenPhrase(last, now) ?: "more than a week ago"),
        onAddMemory = { viewModel.addMemory(person, it) },
        onRemoveMemory = { viewModel.removeMemory(person, it) },
        onMakeFirst = { viewModel.makeFirstMemory(person, it) },
        onRemove = { viewModel.revoke(person) },
    )
  }
}

@Composable
private fun AddPersonCard(
    ui: EnrollUiState,
    connected: Boolean,
    onCapture: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var relationship by rememberSaveable { mutableStateOf("") }
  var recordedBy by rememberSaveable { mutableStateOf("") }
  var consent by rememberSaveable { mutableStateOf(false) }

  Panel(tint = Myst.BlushWash) {
    Text("Add someone", style = MaterialTheme.typography.titleLarge)
    GlassesPhotoCard(
        ui,
        connected,
        "A clear photo of their face, taken with the glasses, lets Myst recognize them.",
        Myst.White,
        onCapture,
        onDiscard,
        accent = Myst.Blush,
    )
    Field(name, { name = it }, "Their name")
    Field(relationship, { relationship = it }, "Who they are to her, e.g. son")
    Field(recordedBy, { recordedBy = it }, "Your name")
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = consent, role = Role.Checkbox, onValueChange = { consent = it }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(
          consent,
          onCheckedChange = null,
          colors = CheckboxDefaults.colors(checkedColor = Myst.LilacStrong, checkmarkColor = Myst.Ink),
      )
      Text(
          "${name.ifBlank { "This person" }} agreed to be recognized by Myst",
          Modifier.padding(start = 8.dp),
          style = MaterialTheme.typography.bodyMedium,
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      PillButton(
          "Save",
          {
            onSave(name, relationship, recordedBy)
            name = ""
            relationship = ""
            recordedBy = ""
            consent = false
          },
          color = Myst.Blush,
          enabled = consent && name.isNotBlank() && recordedBy.isNotBlank(),
      )
      QuietButton("Cancel", onCancel)
    }
    Text(
        "Saved only on this phone. Not uploaded or backed up.",
        style = MaterialTheme.typography.bodySmall,
        color = Myst.InkSoft,
    )
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(
    person: Person,
    photo: ByteArray?,
    lastSeen: String,
    onAddMemory: (String) -> Unit,
    onRemoveMemory: (Int) -> Unit,
    onMakeFirst: (Int) -> Unit,
    onRemove: () -> Unit,
) {
  var confirmRemove by remember(person.id) { mutableStateOf(false) }
  var draft by remember(person.id) { mutableStateOf("") }

  Panel(tint = Myst.BlushWash) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Avatar(person.name, photo, Myst.Blush)
      Column(Modifier.weight(1f)) {
        Text(person.name, style = MaterialTheme.typography.titleLarge)
        Text(person.relationship.ifBlank { "Family" }, style = MaterialTheme.typography.bodyMedium)
        Text(lastSeen, style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
      }
    }

    SmallLabel(
        if (person.memories.isEmpty()) "Add a memory she'll hear after their name"
        else "Memories: the first is said first, then they take turns",
    )
    if (person.memories.isNotEmpty()) {
      Column(Modifier.fillMaxWidth().background(Myst.White, RoundedCornerShape(20.dp)).padding(vertical = 4.dp)) {
        person.memories.forEachIndexed { i, m ->
          if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Myst.Line))
          Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 4.dp)) {
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (i == 0) {
                Tag("Said first", Myst.Blush)
                Spacer(Modifier.width(8.dp))
              }
              Text("by ${m.author}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
              if (i > 0) QuietButton("Say first", { onMakeFirst(i) }, color = Myst.Ink)
              QuietButton("Delete", { onRemoveMemory(i) })
            }
          }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(draft, { draft = it }, "New memory, e.g. You both love the park", Modifier.weight(1f))
      PillButton(
          "Add",
          {
            onAddMemory(draft)
            draft = ""
          },
          color = Myst.Blush,
          enabled = draft.isNotBlank(),
      )
    }

    if (!confirmRemove) {
      QuietButton("Remove ${person.name}", { confirmRemove = true })
    } else {
      Column(
          Modifier.fillMaxWidth().background(Myst.White, RoundedCornerShape(18.dp)).padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
            "Remove ${person.name}? This deletes their photo, memories and visit history from this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          PillButton("Remove", onRemove, color = Myst.Blush)
          QuietButton("Keep", { confirmRemove = false })
        }
      }
    }
  }
}

// MARK: - Medicine

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MedicinePart(viewModel: EnrollViewModel, ui: EnrollUiState, connected: Boolean, onCapture: () -> Unit) {
  var editing by remember { mutableStateOf<Medication?>(null) }
  var formOpen by rememberSaveable { mutableStateOf(false) }

  if (!formOpen) {
    PillButton(
        "Add medicine",
        {
          editing = null
          formOpen = true
        },
        Modifier.fillMaxWidth(),
        color = Myst.Periwinkle,
    )
  }
  AnimatedVisibility(formOpen) {
    MedicineForm(
        viewModel,
        ui,
        connected,
        onCapture,
        editing,
        onDone = {
          formOpen = false
          editing = null
        },
    )
  }

  if (ui.medications.isEmpty() && !formOpen) {
    Panel(tint = Myst.PeriwinkleWash) {
      Text("No medicines yet", style = MaterialTheme.typography.titleLarge)
      Text(
          "Add each one with its time and a photo of the bottle. Myst reminds her and says only what you enter.",
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }

  ui.medications.sortedBy { it.doseMinuteOfDay }.forEach { med ->
    val food = ui.places.firstOrNull { it.id == med.foodPlaceId }?.name
    val stored = ui.places.firstOrNull { it.id == med.storedInPlaceId }?.name
    val dose = DoseSchedule.currentDoseTime(med, System.currentTimeMillis())
    val taken = DoseSchedule.reportedTaken(med, dose, ui.events)
    Panel(tint = Myst.PeriwinkleWash) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text(med.labelName, style = MaterialTheme.typography.titleLarge)
          if (med.plainDescription.isNotBlank()) {
            Text(med.plainDescription, style = MaterialTheme.typography.bodyMedium, color = Myst.InkSoft)
          }
        }
        Text(DoseSchedule.formatTime(med.doseMinuteOfDay), style = MaterialTheme.typography.headlineSmall)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("${med.pillsPerDose} pill${if (med.pillsPerDose == 1) "" else "s"}", Myst.White)
        Tag("within ${med.windowMinutes} min", Myst.White)
        if (med.withWater) Tag("with water", Myst.Periwinkle)
        if (med.withFood) Tag("with food" + (food?.let { " · ${it.lowercase()}" } ?: ""), Myst.Blush)
        stored?.let { Tag("kept in the ${it.lowercase()}", Myst.Lilac) }
      }
      if (med.storageNote.isNotBlank()) {
        Text(med.storageNote, style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
      }
      PhotoStrip(med.bottlePhotoFiles, viewModel)
      Text(
          taken?.let { "Reported taken at ${DoseSchedule.formatTimeOfDay(it.atMillis)}" }
              ?: "Dose at ${DoseSchedule.formatTimeOfDay(dose)} · not reported taken",
          style = MaterialTheme.typography.labelLarge,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        PillButton("Mark as taken", { viewModel.markTaken(med) }, color = Myst.White, enabled = taken == null)
        QuietButton("Edit", {
          editing = med
          formOpen = true
        }, color = Myst.Ink)
        QuietButton("Delete", { viewModel.deleteMedication(med) })
      }
    }
  }
}

@Composable
private fun MedicineForm(
    viewModel: EnrollViewModel,
    ui: EnrollUiState,
    connected: Boolean,
    onCapture: () -> Unit,
    editing: Medication?,
    onDone: () -> Unit,
) {
  // Keyed on the medication being edited, so opening another one reloads the fields.
  val key = editing?.id ?: "new"
  var labelName by remember(key) { mutableStateOf(editing?.labelName ?: "") }
  var description by remember(key) { mutableStateOf(editing?.plainDescription ?: "") }
  var pills by remember(key) { mutableStateOf((editing?.pillsPerDose ?: 1).toString()) }
  val dm = editing?.doseMinuteOfDay ?: 8 * 60
  var hour by remember(key) { mutableStateOf((if (dm / 60 % 12 == 0) 12 else dm / 60 % 12).toString()) }
  var minuteText by remember(key) { mutableStateOf("%02d".format(dm % 60)) }
  var pm by remember(key) { mutableStateOf(dm / 60 >= 12) }
  var window by remember(key) { mutableStateOf((editing?.windowMinutes ?: 60).toString()) }
  var withFood by remember(key) { mutableStateOf(editing?.withFood ?: false) }
  var withWater by remember(key) { mutableStateOf(editing?.withWater ?: true) }
  var foodPlaceId by remember(key) { mutableStateOf(editing?.foodPlaceId) }
  var storedInId by remember(key) { mutableStateOf(editing?.storedInPlaceId) }
  var storageNote by remember(key) { mutableStateOf(editing?.storageNote ?: "") }

  val minute = toMinuteOfDay(hour, minuteText, pm)
  val pillCount = pills.toIntOrNull()
  val windowMin = window.toIntOrNull()
  val valid = labelName.isNotBlank() && minute != null && (pillCount ?: 0) >= 1 && (windowMin ?: 0) >= 1

  Panel(tint = Myst.PeriwinkleWash) {
    Text(if (editing == null) "Add medicine" else "Edit ${editing.labelName}", style = MaterialTheme.typography.titleLarge)
    GlassesPhotoCard(
        ui,
        connected,
        "Hold the bottle close with the label facing the glasses. Add two or three photos over time.",
        Myst.White,
        onCapture,
        viewModel::discardPhoto,
        accent = Myst.Periwinkle,
    )
    Field(labelName, { labelName = it }, "Name on the label, e.g. Lisinopril 10 mg")
    Field(description, { description = it }, "How she'd describe it, e.g. the small white bottle")

    SmallLabel("When")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(hour, { hour = it.filter(Char::isDigit).take(2) }, "Hour", Modifier.weight(1f), number = true)
      Field(minuteText, { minuteText = it.filter(Char::isDigit).take(2) }, "Min", Modifier.weight(1f), number = true)
      Choice("AM", !pm) { pm = false }
      Choice("PM", pm) { pm = true }
    }
    if (minute == null) Text("Use an hour from 1 to 12 and minutes from 00 to 59.", color = Myst.Rose, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field(pills, { pills = it.filter(Char::isDigit) }, "Pills each time", Modifier.weight(1f), number = true)
      Field(window, { window = it.filter(Char::isDigit) }, "Window (min)", Modifier.weight(1f), number = true)
    }
    Text(
        "Myst says the pill count exactly as you enter it and never gives dosing advice.",
        style = MaterialTheme.typography.bodySmall,
        color = Myst.InkSoft,
    )

    SwitchRow("Take with water", withWater) { withWater = it }
    SwitchRow("Take with food", withFood) { withFood = it }
    if (withFood) {
      SmallLabel("Where she gets food")
      PlacePicker(ui.places, foodPlaceId, defaultName = "Kitchen") { foodPlaceId = it }
    }
    SmallLabel("Where the bottle is kept")
    PlacePicker(ui.places, storedInId, defaultName = null) { storedInId = it }
    Field(storageNote, { storageNote = it }, "Exact spot, e.g. cabinet above the sink")

    Row(verticalAlignment = Alignment.CenterVertically) {
      PillButton(
          if (editing == null) "Save medicine" else "Save changes",
          {
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
                attachPhoto = true,
            )
            onDone()
          },
          color = Myst.Periwinkle,
          enabled = valid,
      )
      QuietButton("Cancel", onDone)
    }
  }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
  Box(
      Modifier.clip(CircleShape)
          .background(if (selected) Myst.Periwinkle else Myst.White)
          .selectable(selected, role = Role.RadioButton, onClick = onClick)
          .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge)
  }
}
