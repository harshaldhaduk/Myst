package com.myst.ui

import android.content.Intent
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myst.core.guard.ResponseGuard
import com.myst.core.records.Medication
import com.myst.features.meds.DoseSchedule
import com.myst.features.meds.DoseStage
import com.myst.ui.theme.Myst
import java.util.Calendar

// Caregiver setup for simple daily reminders. Myst speaks only caregiver-entered details (D6).

@Composable
internal fun RemindersPart(viewModel: EnrollViewModel, ui: EnrollUiState) {
  var editing by remember { mutableStateOf<Medication?>(null) }
  var formOpen by rememberSaveable { mutableStateOf(false) }

  if (!ui.exactAlarms) ExactAlarmWarning()

  if (!formOpen) {
    PillButton(
        "Add a reminder",
        {
          editing = null
          formOpen = true
        },
        Modifier.fillMaxWidth(),
        color = Myst.Periwinkle,
    )
  }
  AnimatedVisibility(formOpen) {
    ReminderForm(
        viewModel,
        ui,
        editing,
        onDone = {
          formOpen = false
          editing = null
        },
    )
  }

  if (ui.medications.isEmpty() && !formOpen) {
    Panel(tint = Myst.PeriwinkleWash) {
      Text("No reminders yet", style = MaterialTheme.typography.titleLarge)
      Text(
          "Add each medicine with its time and where it's kept. Myst reminds her and says only what you enter.",
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }

  ui.medications.sortedBy { it.doseMinuteOfDay }.forEach { med ->
    var confirmDelete by remember(med.id) { mutableStateOf(false) }
    Panel(tint = Myst.PeriwinkleWash) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text(med.labelName, style = MaterialTheme.typography.titleLarge)
          Text("Every day", style = MaterialTheme.typography.bodyMedium, color = Myst.InkSoft)
        }
        Text(DoseSchedule.formatTime(med.doseMinuteOfDay), style = MaterialTheme.typography.headlineSmall)
      }
      if (med.withWater) Tag("with water", Myst.Periwinkle)
      if (med.withFood) Tag("with food", Myst.Blush)
      med.spokenWhere()?.let { Text("Kept $it", style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft) }

      if (!confirmDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          QuietButton("Edit", {
            editing = med
            formOpen = true
          }, color = Myst.Ink)
          QuietButton("Delete", { confirmDelete = true })
        }
      } else {
        Column(
            Modifier.fillMaxWidth().background(Myst.White, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Delete the reminder for ${med.labelName}? Myst stops reminding her.", style = MaterialTheme.typography.bodyMedium)
          Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton("Delete", { viewModel.deleteMedication(med) }, color = Myst.Periwinkle)
            QuietButton("Keep", { confirmDelete = false })
          }
        }
      }
    }
  }
}

@Composable
private fun ReminderForm(viewModel: EnrollViewModel, ui: EnrollUiState, editing: Medication?, onDone: () -> Unit) {
  // Keyed on the reminder being edited, so opening another one reloads the fields.
  val key = editing?.id ?: "new"
  var labelName by remember(key) { mutableStateOf(editing?.labelName ?: "") }
  var minuteOfDay by remember(key) { mutableStateOf(editing?.doseMinuteOfDay ?: defaultReminderMinute()) }
  var withFood by remember(key) { mutableStateOf(editing?.withFood ?: false) }
  var withWater by remember(key) { mutableStateOf(editing?.withWater ?: true) }
  var storageLocation by remember(key) { mutableStateOf(editing?.storageLocation ?: "") }
  val context = LocalContext.current

  val draft =
      Medication(
          id = editing?.id ?: "",
          labelName = labelName.trim(),
          plainDescription = editing?.plainDescription.orEmpty(),
          pillsPerDose = editing?.pillsPerDose ?: 1,
          doseMinuteOfDay = minuteOfDay,
          windowMinutes = editing?.windowMinutes ?: 60,
          withFood = withFood,
          withWater = withWater,
          storageLocation = storageLocation.trim(),
          createdAtMillis = 0,
      )
  // The reminder as it will be spoken; a line the guard would drop is flagged now, not at dose time.
  val spoken = DoseSchedule.spokenLines(draft, DoseStage.REMIND)
  val blocked = spoken.filter { ResponseGuard.check(it, null, ui.people) is ResponseGuard.Decision.Block }
  val valid = labelName.isNotBlank()

  Panel(tint = Myst.PeriwinkleWash) {
    Text(if (editing == null) "Add a reminder" else "Edit ${editing.labelName}", style = MaterialTheme.typography.titleLarge)
    Field(labelName, { labelName = it }, "Reminder name, e.g. morning medicine")

    SmallLabel("Time · every day")
    PillButton(DoseSchedule.formatTime(minuteOfDay), {
      TimePickerDialog(
              context,
              { _, hour, minute -> minuteOfDay = hour * 60 + minute },
              minuteOfDay / 60,
              minuteOfDay % 60,
              DateFormat.is24HourFormat(context),
          )
          .show()
    }, color = Myst.White)
    Text("Tap the time to change it. Add another reminder for a second time of day.", style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)

    Field(storageLocation, { storageLocation = it }, "Where it's kept (optional)")
    SwitchRow("Take with water", withWater) { withWater = it }
    SwitchRow("Take with food", withFood) { withFood = it }

    if (labelName.isNotBlank()) {
      Column(
          Modifier.fillMaxWidth().background(Myst.White, RoundedCornerShape(18.dp)).padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SmallLabel("At ${DoseSchedule.formatTime(minuteOfDay)} Myst says")
        Text("“${spoken.joinToString(" ")}”", style = MaterialTheme.typography.bodyLarge)
        if (blocked.isNotEmpty()) {
          Text(
              "Myst will skip: ${blocked.joinToString(" ")} Shorten it, or avoid a family member's name.",
              style = MaterialTheme.typography.bodySmall,
              color = Myst.Rose,
          )
        }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      PillButton(
          if (editing == null) "Save reminder" else "Save changes",
          {
            viewModel.saveMedication(draft)
            onDone()
          },
          color = Myst.Periwinkle,
          enabled = valid,
      )
      QuietButton("Cancel", onDone)
    }
  }
}

/** Without "Alarms & reminders" access Android may deliver reminders a few minutes late. */
@Composable
internal fun ExactAlarmWarning() {
  val context = LocalContext.current
  Panel(tint = Myst.BlushWash, radius = 22.dp, padding = 16.dp) {
    Text("Reminders may run a few minutes late", style = MaterialTheme.typography.titleMedium)
    Text(
        "Allow Myst to set alarms and reminders so dose reminders arrive on time.",
        style = MaterialTheme.typography.bodySmall,
    )
    PillButton(
        "Open alarm settings",
        {
          runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
            )
          }
        },
        color = Myst.Blush,
    )
  }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Switch(
        checked,
        onCheckedChange = null,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = Myst.White,
                checkedTrackColor = Myst.LilacStrong,
                checkedBorderColor = Myst.LilacStrong,
                uncheckedThumbColor = Myst.Lilac,
                uncheckedTrackColor = Myst.White,
                uncheckedBorderColor = Myst.Lilac,
            ),
    )
    Text("  $label")
  }
}

/** Start a new reminder at the next five-minute mark so a quick demo can be scheduled ahead. */
private fun defaultReminderMinute(): Int =
    Calendar.getInstance().run { ((get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) + 5) / 5 * 5) % 1440 }
