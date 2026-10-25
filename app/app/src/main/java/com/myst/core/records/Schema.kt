package com.myst.core.records

import org.json.JSONArray
import org.json.JSONObject

// The one shared record schema (hub/BUILDING.md: one definition, change callers together).
// Family-entered on the phone: people, medication reminders, and the timeline of events.

/** Who agreed, who recorded it, and when. A person without consent is never stored. */
data class Consent(
    val recordedBy: String,
    val recordedAtMillis: Long,
)

/** Where the reference photo came from. MOCK photos are placeholders from the Mock Device Kit. */
enum class PhotoSource { GLASSES, MOCK_DEVICE_KIT }

data class Memory(
    val text: String,
    val author: String,
)

data class Person(
    val id: String,
    val name: String,
    val relationship: String,
    val consent: Consent,
    /** File name inside app-private storage (never a path outside the app, never synced). */
    val photoFile: String?,
    val photoSource: PhotoSource?,
    val memories: List<Memory> = emptyList(),
    val createdAtMillis: Long,
    /** D30: a deleted demo seed must not reappear when the app restarts. */
    val demoMemorySeeded: Boolean = false,
) {
  /** Memory edits return a new Person; an out-of-range index changes nothing. */
  fun withMemory(memory: Memory) = copy(memories = memories + memory)

  fun withoutMemory(index: Int) =
      if (index in memories.indices) copy(memories = memories.filterIndexed { i, _ -> i != index }) else this

  /** Spoken in order, starting with the first; Myst rotates on repeat asks. */
  fun withMemoryFirst(index: Int) =
      if (index in memories.indices) {
        copy(memories = listOf(memories[index]) + memories.filterIndexed { i, _ -> i != index })
      } else this

  fun toJson(): JSONObject =
      JSONObject()
          .put("id", id)
          .put("name", name)
          .put("relationship", relationship)
          .put(
              "consent",
              JSONObject()
                  .put("recordedBy", consent.recordedBy)
                  .put("recordedAtMillis", consent.recordedAtMillis),
          )
          .put("photoFile", photoFile ?: JSONObject.NULL)
          .put("photoSource", photoSource?.name ?: JSONObject.NULL)
          .put(
              "memories",
              JSONArray().apply {
                memories.forEach { put(JSONObject().put("text", it.text).put("author", it.author)) }
              },
          )
          .put("createdAtMillis", createdAtMillis)
          .put("demoMemorySeeded", demoMemorySeeded)

  companion object {
    fun fromJson(o: JSONObject): Person {
      val c = o.getJSONObject("consent")
      val mems = o.optJSONArray("memories") ?: JSONArray()
      return Person(
          id = o.getString("id"),
          name = o.getString("name"),
          relationship = o.getString("relationship"),
          consent = Consent(c.getString("recordedBy"), c.getLong("recordedAtMillis")),
          photoFile = o.optString("photoFile").takeIf { !o.isNull("photoFile") && it.isNotEmpty() },
          photoSource =
              o.optString("photoSource").takeIf { !o.isNull("photoSource") && it.isNotEmpty() }
                  ?.let { PhotoSource.valueOf(it) },
          memories =
              (0 until mems.length()).map {
                val m = mems.getJSONObject(it)
                Memory(m.getString("text"), m.getString("author"))
              },
          createdAtMillis = o.getLong("createdAtMillis"),
          demoMemorySeeded = o.optBoolean("demoMemorySeeded", false),
      )
    }
  }
}

/**
 * Caregiver-entered medication reminder (D6). Myst speaks [pillsPerDose] exactly as entered and
 * never advises on dosing. Older records also held place ids and bottle photo paths; [fromJson]
 * ignores those fields and [HouseholdStore] migrates the place name into [storageLocation].
 */
data class Medication(
    val id: String,
    /** Name as printed on the label, e.g. "Lisinopril 10 mg". */
    val labelName: String,
    /** In the wearer's words, e.g. "the small white bottle". */
    val plainDescription: String,
    val pillsPerDose: Int,
    /** Dose time, minutes after midnight. */
    val doseMinuteOfDay: Int,
    /** How long after the dose time it can still be taken. */
    val windowMinutes: Int,
    val withFood: Boolean,
    val withWater: Boolean,
    /** Where it's kept, free text, e.g. "in the cabinet above the sink". */
    val storageLocation: String,
    val createdAtMillis: Long,
) {
  /** [storageLocation] as spoken after "It's": "in the cabinet above the sink", or null. */
  fun spokenWhere(): String? = withPreposition(storageLocation)

  fun toJson(): JSONObject =
      JSONObject()
          .put("id", id)
          .put("labelName", labelName)
          .put("plainDescription", plainDescription)
          .put("pillsPerDose", pillsPerDose)
          .put("doseMinuteOfDay", doseMinuteOfDay)
          .put("windowMinutes", windowMinutes)
          .put("withFood", withFood)
          .put("withWater", withWater)
          .put("storageLocation", storageLocation)
          .put("createdAtMillis", createdAtMillis)

  companion object {
    fun fromJson(o: JSONObject) =
        Medication(
            id = o.getString("id"),
            labelName = o.getString("labelName"),
            plainDescription = o.optString("plainDescription"),
            pillsPerDose = o.getInt("pillsPerDose"),
            doseMinuteOfDay = o.getInt("doseMinuteOfDay"),
            windowMinutes = o.getInt("windowMinutes"),
            withFood = o.optBoolean("withFood"),
            withWater = o.optBoolean("withWater"),
            // Records saved before the Reminders page called this field "storageNote".
            storageLocation = o.optString("storageLocation", o.optString("storageNote")),
            createdAtMillis = o.getLong("createdAtMillis"),
        )
  }
}

/** "cabinet above the sink" -> "in the cabinet above the sink"; "on the counter" stays as typed. */
internal fun withPreposition(text: String): String? =
    text.trim().takeIf(String::isNotEmpty)?.let { t ->
      if (Regex("^(in|on|under|next to|beside|behind|by|at)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) t
      else "in the $t"
    }

/** Timeline entries. A dose is only ever "reported taken" (D6): a log is not proof. */
enum class EventType {
  VISIT,
  /** The dose-time reminder was spoken (or tried: no speech if the phone could not). */
  DOSE_REMINDED,
  /** A +10 or +20 minute re-reminder. */
  DOSE_REREMINDED,
  DOSE_REPORTED_TAKEN,
  DOSE_BLOCKED_DOUBLE,
  DOSE_MISSED,
  ALERT,
}

data class Event(
    val id: String,
    val type: EventType,
    val atMillis: Long,
    val personId: String? = null,
    val medicationId: String? = null,
    /** Short detail, e.g. "marked on phone". Never a transcript or a photo. */
    val detail: String = "",
) {
  fun toJson(): JSONObject =
      JSONObject()
          .put("id", id)
          .put("type", type.name)
          .put("atMillis", atMillis)
          .put("personId", personId ?: JSONObject.NULL)
          .put("medicationId", medicationId ?: JSONObject.NULL)
          .put("detail", detail)

  companion object {
    fun fromJson(o: JSONObject) =
        Event(
            id = o.getString("id"),
            type = EventType.valueOf(o.getString("type")),
            atMillis = o.getLong("atMillis"),
            personId = o.optString("personId").takeIf { !o.isNull("personId") && it.isNotEmpty() },
            medicationId =
                o.optString("medicationId").takeIf { !o.isNull("medicationId") && it.isNotEmpty() },
            detail = o.optString("detail"),
        )
  }
}
