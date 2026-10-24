package com.myst.core.records

import org.json.JSONArray
import org.json.JSONObject

// The one shared record schema (hub/BUILDING.md: one definition, change callers together).
// Family-entered on the phone. Medication and Event records are added here when those features land.

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
) {
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
      )
    }
  }
}
