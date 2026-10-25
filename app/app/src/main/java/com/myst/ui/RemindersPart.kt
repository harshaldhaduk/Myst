package com.myst.ui

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myst.core.guard.ResponseGuard
import com.myst.core.records.Event
import com.myst.core.records.EventType
import com.myst.core.records.Medication
import com.myst.features.meds.DoseSchedule
import com.myst.features.meds.DoseStage
import com.myst.ui.theme.Myst
import java.util.Calendar

// Caregiver setup for Lane 3: medication reminders, like a schedule. Myst speaks only what is
// entered here (D6), and a dose is only ever "reported taken".

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

  val now = System.currentTimeMillis()
  ui.medications.sortedBy { it.doseMinuteOfDay }.forEach { med ->
    val dose = DoseSchedule.currentDoseTime(med, now)
    val taken = DoseSchedule.reportedTaken(med, dose, ui.events)
    var confirmDelete by remember(med.id) { mutableStateOf(false) }
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
        if (med.withFood) Tag("with food", Myst.Blush)
      }
      med.spokenWhere()?.let { Text("Kept $it", style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft) }

      SmallLabel("Today")
      TodayTimeline(med, todaysEvents(med, ui.events, now))
      Text(
          taken?.let { "Reported taken at ${DoseSchedule.formatTimeOfDay(it.atMillis)}" }
              ?: "Dose at ${DoseSchedule.formatTimeOfDay(dose)} · not reported taken",
          style = MaterialTheme.typography.labelLarge,
      )

      if (!confirmDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          PillButton("Mark reported taken", { viewModel.markTaken(med) }, color = Myst.White, enabled = taken == null)
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

  if (ui.medications.isNotEmpty()) {
    Text(
        "A missed dose alerts the caregiver on this phone. WhatsApp alerts are a mock for now (Q13). " +
            "\"Reported taken\" means someone said so; it is not proof.",
        style = MaterialTheme.typography.bodySmall,
        color = Myst.InkSoft,
    )
  }
}

/** This medication's reminder events since midnight, oldest first. */
private fun todaysEvents(med: Medication, events: List<Event>, now: Long): List<Event> {
  val midnight =
      Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
      }
  return events.filter { it.medicationId == med.id && it.atMillis >= midnight }.sortedBy { it.atMillis }
}

@Composable
private fun TodayTimeline(med: Medication, today: List<Event>) {
  val rows =
      today.mapNotNull { e ->
        when (e.type) {
          EventType.DOSE_REMINDED -> "Reminded"
          EventType.DOSE_REREMINDED -> "Reminded again"
          EventType.DOSE_REPORTED_TAKEN -> "Reported taken"
          EventType.DOSE_BLOCKED_DOUBLE -> "Reported again, not logged twice"
          EventType.DOSE_MISSED -> "Missed: not reported taken"
          EventType.VISIT, EventType.ALERT -> null
        }?.let { it to DoseSchedule.formatTimeOfDay(e.atMillis) }
      }
  Column(Modifier.fillMaxWidth().background(Myst.White, RoundedCornerShape(20.dp)).padding(vertical = 4.dp)) {
    if (rows.isEmpty()) {
      Text(
          "Nothing yet today. Reminder at ${DoseSchedule.formatTime(med.doseMinuteOfDay)}.",
          Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = Myst.InkSoft,
      )
    }
    rows.forEachIndexed { i, (what, time) ->
      if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Myst.Line))
      Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            what,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (what.startsWith("Missed")) Myst.Rose else Myst.Ink,
        )
        Text(time, style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
      }
    }
  }
}

@Composable
private fun ReminderForm(viewModel: EnrollViewModel, ui: EnrollUiState, editing: Medication?, onDone: () -> Unit) {
  // Keyed on the reminder being edited, so opening another one reloads the fields.
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
  var storageLocation by remember(key) { mutableStateOf(editing?.storageLocation ?: "") }

  val minute = toMinuteOfDay(hour, minuteText, pm)
  val pillCount = pills.toIntOrNull()
  val windowMin = window.toIntOrNull()
  val draft =
      Medication(
          id = editing?.id ?: "",
          labelName = labelName.trim(),
          plainDescription = description.trim(),
          pillsPerDose = pillCount ?: 1,
          doseMinuteOfDay = minute ?: 0,
          windowMinutes = windowMin ?: 60,
          withFood = withFood,
          withWater = withWater,
          storageLocation = storageLocation.trim(),
          createdAtMillis = 0,
      )
  // The reminder as it will be spoken; a line the guard would drop is flagged now, not at dose time.
  val spoken = DoseSchedule.spokenLines(draft, DoseStage.REMIND)
  val blocked = spoken.filter { ResponseGuard.check(it, null, ui.people) is ResponseGuard.Decision.Block }
  val valid = labelName.isNotBlank() && minute != null && (pillCount ?: 0) >= 1 && (windowMin ?: 0) >= 1

  Panel(tint = Myst.PeriwinkleWash) {
    Text(if (editing == null) "Add a reminder" else "Edit ${editing.labelName}", style = MaterialTheme.typography.titleLarge)
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
        "Myst says the pill count exactly as you enter it and never gives dosing advice. " +
            "For a second time of day, add another reminder.",
        style = MaterialTheme.typography.bodySmall,
        color = Myst.InkSoft,
    )

    Field(storageLocation, { storageLocation = it }, "Where it's kept, e.g. in the bathroom cabinet")
    SwitchRow("Take with water", withWater) { withWater = it }
    SwitchRow("Take with food", withFood) { withFood = it }

    if (labelName.isNotBlank()) {
      Column(
          Modifier.fillMaxWidth().background(Myst.White, RoundedCornerShape(18.dp)).padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        SmallLabel("At ${minute?.let(DoseSchedule::formatTime) ?: "dose time"} Myst says")
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

/** 12-hour entry to minutes after midnight; 12 AM is midnight, 12 PM is noon. */
internal fun toMinuteOfDay(hour: String, minute: String, pm: Boolean): Int? {
  val h = hour.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
  val m = minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
  return ((h % 12) + if (pm) 12 else 0) * 60 + m
}
