package com.myst.features.meds

import com.myst.core.records.Medication
import java.util.Calendar

/** Old stage names remain so alarms scheduled by earlier APKs can be ignored safely. */
enum class DoseStage { REMIND, REREMIND_1, REREMIND_2, WINDOW_CLOSED }

/** One daily reminder time, kept free of Android. */
object DoseSchedule {
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

  /** The next alarm after [now], today if the time has not passed, otherwise tomorrow. */
  fun nextReminderTime(med: Medication, now: Long): Long {
    val today = doseTimeOn(med, now)
    return if (today > now) today else doseTimeOn(med, now + 24 * 60 * 60_000L)
  }

  /**
   * What Myst says for a stage, one short line each so each passes the guard on its own. Every
   * fact is the caregiver's (D6): name, where it's kept, the water and food rule. No dosing advice.
   */
  fun spokenLines(med: Medication, stage: DoseStage): List<String> {
    val name = med.labelName
    val where = med.spokenWhere()
    return when (stage) {
      DoseStage.REMIND ->
          listOfNotNull(
              "It's time for your $name.",
              where?.let { "It's $it." },
              if (med.withWater) "Take it with a glass of water." else null,
              if (med.withFood) "Have something to eat first." else null,
          )
      DoseStage.REREMIND_1, DoseStage.REREMIND_2, DoseStage.WINDOW_CLOSED -> emptyList()
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
