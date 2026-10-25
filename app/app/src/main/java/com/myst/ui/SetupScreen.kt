package com.myst.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.myst.core.records.Person
import com.myst.core.records.VisitMemory
import com.myst.ui.theme.Myst

private enum class SetupPart(val title: String, val tint: Color) {
  FAMILY("Family", Myst.Blush),
  REMINDERS("Reminders", Myst.Periwinkle),
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
          "The people and medicine reminders Myst helps with.",
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
      SetupPart.REMINDERS -> RemindersPart(viewModel, ui)
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
