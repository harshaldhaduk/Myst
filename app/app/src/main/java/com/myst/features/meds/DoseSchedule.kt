package com.myst.features.meds

import com.myst.core.records.Event
import com.myst.core.records.EventType
import com.myst.core.records.Medication
import java.util.Calendar

/** The reminder steps for one dose (PRD R6, R13). */
enum class DoseStage { REMIND, REREMIND_1, REREMIND_2, WINDOW_CLOSED }

/** Pure timing rules, kept free of Android so they can be unit-tested (D13). */
object DoseSchedule {
  /** Normal timing: re-remind at +10 and +20 min, alert when the window closes. */
  val NORMAL = Timing(rereminds = listOf(10, 20), windowOverride = null)

  /** Demo timing: re-remind at +1 and +2 min, window closes at +3. */
  val FAST_DEMO = Timing(rereminds = listOf(1, 2), windowOverride = 3)

  data class Timing(val rereminds: List<Int>, val windowOverride: Int?)

  /** Minutes after the dose time at which each stage fires; re-reminds past the window are dropped. */
  fun offsets(med: Medication, timing: Timing): Map<DoseStage, Int> {
    val window = timing.windowOverride ?: med.windowMinutes
    val stages = mutableMapOf(DoseStage.REMIND to 0)
    timing.rereminds.getOrNull(0)?.takeIf { it < window }?.let { stages[DoseStage.REREMIND_1] = it }
    timing.rereminds.getOrNull(1)?.takeIf { it < window }?.let { stages[DoseStage.REREMIND_2] = it }
    stages[DoseStage.WINDOW_CLOSED] = window
    return stages
  }

  /** The dose time on the day of [dayMillis]. */
  fun doseTimeOn(med: Medication, dayMillis: Long): Long =
      Calendar.getInstance().run {
        timeInMillis = dayMillis
        set(Calendar.HOUR_OF_DAY, med.doseMinuteOfDay / 60)
        set(Calendar.MINUTE, med.doseMinuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
      }

  /**
   * Which dose a schedule should cover at [now]: today's if its window is still open, otherwise
   * tomorrow's.
   */
  fun currentDoseTime(med: Medication, now: Long, timing: Timing = NORMAL): Long {
    val today = doseTimeOn(med, now)
    val closes = today + (timing.windowOverride ?: med.windowMinutes) * 60_000L
    return if (now < closes) today else doseTimeOn(med, now + 24 * 60 * 60_000L)
  }

  /** R11: the report for the dose at [doseTime], if one was logged since 12 hours before it. */
  fun reportedTaken(med: Medication, doseTime: Long, events: List<Event>): Event? =
      events.lastOrNull {
        it.type == EventType.DOSE_REPORTED_TAKEN &&
            it.medicationId == med.id &&
            it.atMillis >= doseTime - 12 * 60 * 60_000L
      }

  /**
   * What Myst says for a stage, one short line each so each passes the guard on its own. Every
   * fact is the caregiver's (D6): name, pill count, bottle description, location, and water/food rules.
   */
  fun spokenLines(med: Medication, stage: DoseStage): List<String> {
    val name = med.labelName
    val where = med.spokenWhere()
    val count = "Take ${med.pillsPerDose} ${if (med.pillsPerDose == 1) "pill" else "pills"}."
    val bottle = med.plainDescription.trim().trimEnd('.').takeIf { it.isNotBlank() }?.let { "Look for $it." }
    return when (stage) {
      DoseStage.REMIND ->
          listOfNotNull(
              "It's time for your $name.",
              count,
              bottle,
              where?.let { "It's $it." },
              if (med.withWater) "Take it with a glass of water." else null,
              if (med.withFood) "Have something to eat first." else null,
          )
      DoseStage.REREMIND_1, DoseStage.REREMIND_2 ->
          listOfNotNull("Just a reminder, your $name is ready for you" + (where?.let { ", $it." } ?: "."), count, bottle)
      DoseStage.WINDOW_CLOSED -> emptyList()
    }
  }

  /** "8:00 AM" */
  fun formatTime(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
  }

  fun formatTimeOfDay(millis: Long): String =
      Calendar.getInstance().run {
        timeInMillis = millis
        formatTime(get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE))
      }
}
