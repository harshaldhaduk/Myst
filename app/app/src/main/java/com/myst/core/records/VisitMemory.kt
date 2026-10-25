package com.myst.core.records

import com.myst.core.guard.ResponseGuard
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Visit memory (D18): when did the wearer last see an enrolled person? A sighting is a VISIT event
 * logged when Myst recognized the person on request. It says "last recognized by Myst", never proof
 * of a visit, and holds no frames or faces.
 *
 * Pure logic, no Android types, so it can be checked on the JVM once JUnit is accepted (D13).
 */
object VisitMemory {
  /** Sightings closer together than this are one encounter. */
  const val SAME_ENCOUNTER_MS = 30 * 60_000L

  /** A repeat sighting this soon adds nothing; do not store it. */
  const val DEDUPE_MS = 5 * 60_000L

  const val KEEP_DAYS = 30L

  /** Older than this, the memory is stale and Myst stays quiet about it. */
  const val MAX_SPOKEN_DAYS = 6L

  /**
   * The last sighting of an earlier encounter, or null if there is none. Sightings chained within
   * [SAME_ENCOUNTER_MS] of [now] belong to the current encounter and are skipped, so asking twice
   * in one meeting gives the same answer ("at 1 PM"), not "2 minutes ago".
   */
  fun previousEncounter(sightings: List<Long>, now: Long): Long? {
    var edge = now
    for (t in sightings.filter { it <= now }.sortedDescending()) {
      if (edge - t <= SAME_ENCOUNTER_MS) edge = t else return t
    }
    return null
  }

  /** "today at 1 PM", "yesterday at 1:30 PM", "on Tuesday at 6 PM"; null when too old to say. */
  fun whenPhrase(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
    val t = Instant.ofEpochMilli(then).atZone(zone)
    val n = Instant.ofEpochMilli(now).atZone(zone)
    val days = ChronoUnit.DAYS.between(t.toLocalDate(), n.toLocalDate())
    if (days < 0 || days > MAX_SPOKEN_DAYS) return null
    val hour = (t.hour + 11) % 12 + 1
    val suffix = if (t.hour < 12) "AM" else "PM"
    val clock = if (t.minute == 0) "$hour $suffix" else "$hour:%02d $suffix".format(t.minute)
    val day =
        when (days) {
          0L -> "today"
          1L -> "yesterday"
          else -> "on " + t.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    return "$day at $clock"
  }

  /** The sentence for a person, or null for a first sighting or a stale one. */
  fun sentence(name: String, previous: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
    val at = previous?.let { whenPhrase(it, now, zone) } ?: return null
    return "You saw $name $at."
  }

  /**
   * Name and relationship always; then the visit sentence; then the caregiver memory only if the
   * whole line still fits the guard's word limit (otherwise the guard would silence all of it).
   */
  fun compose(base: String, visit: String?, memory: String?): String {
    var line = base
    for (extra in listOfNotNull(visit, memory)) {
      val next = "$line $extra"
      if (next.trim().split(Regex("\\s+")).size <= ResponseGuard.MAX_WORDS) line = next
    }
    return line
  }

  /** Whether an event is old enough to delete (D18 retention). */
  fun expired(event: Event, now: Long): Boolean =
      event.type == EventType.VISIT && now - event.atMillis > KEEP_DAYS * 24 * 3_600_000L
}
