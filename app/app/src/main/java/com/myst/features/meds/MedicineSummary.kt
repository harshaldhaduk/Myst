package com.myst.features.meds

import com.myst.core.records.Medication

/**
 * What Myst says about an identified medicine. Every fact comes from the caregiver's records
 * (D6): name, pill counts, times and the food/water rule. Muse only decides which medicine it is.
 */
object MedicineSummary {
  /** Medications that are the same product (same label name), e.g. a morning and evening dose. */
  fun sameProduct(a: Medication, b: Medication): Boolean = key(a) == key(b)

  private fun key(m: Medication) = m.labelName.lowercase().replace(Regex("[^a-z0-9]"), "")

  /** Short lines, each spoken on its own and each under the guard's 20-word limit. */
  fun lines(schedules: List<Medication>): List<String> {
    val first = schedules.firstOrNull() ?: return emptyList()
    val sorted = schedules.sortedBy { it.doseMinuteOfDay }
    return listOfNotNull(
        "This is your ${first.labelName}.",
        doseLine(sorted),
        ruleLine(sorted),
    )
  }

  private fun doseLine(s: List<Medication>): String {
    val times = s.map { spokenTime(it.doseMinuteOfDay) }
    val sameCount = s.map { it.pillsPerDose }.distinct().size == 1
    return if (sameCount) {
      val pills = pills(s.first().pillsPerDose)
      val often =
          when (s.size) {
            1 -> ""
            2 -> " twice a day,"
            3 -> " three times a day,"
            else -> " ${s.size} times a day,"
          }
      "You take $pills$often at ${join(times)}."
    } else {
      "You take " + join(s.map { "${pills(it.pillsPerDose)} at ${spokenTime(it.doseMinuteOfDay)}" }) + "."
    }
  }

  private fun ruleLine(s: List<Medication>): String? {
    val food = s.any { it.withFood }
    val water = s.any { it.withWater }
    return when {
      food && water -> "Take it with food and a glass of water, so have a snack first."
      food -> "Take it with food, so have a snack first."
      water -> "Take it with a glass of water."
      else -> null
    }
  }

  private fun pills(n: Int) = if (n == 1) "1 pill" else "$n pills"

  /** "8 AM", "9:30 PM" */
  fun spokenTime(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    val ampm = if (h < 12) "AM" else "PM"
    return if (m == 0) "$h12 $ampm" else "%d:%02d %s".format(h12, m, ampm)
  }

  private fun join(items: List<String>): String =
      when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
      }
}
