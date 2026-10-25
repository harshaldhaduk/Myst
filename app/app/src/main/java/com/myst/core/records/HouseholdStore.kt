package com.myst.core.records

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Medication reminders and timeline events, in app-private noBackupFilesDir like [PeopleStore].
 * No photos: the Places tab and the bottle check were removed (tag
 * archive/bottle-check-places-home-buttons), and [migrateLegacy] cleans up what they left behind.
 */
class HouseholdStore(context: Context) {
  private val dir = File(context.noBackupFilesDir, "household").apply { mkdirs() }

  // MARK: - Medications

  @Synchronized
  fun medications(): List<Medication> {
    migrateLegacy()
    return read("medications.json", Medication::fromJson)
  }

  /** [draft]'s id is kept when editing; a blank id makes a new reminder. */
  @Synchronized
  fun saveMedication(draft: Medication): Medication {
    val existing = medications().firstOrNull { it.id == draft.id }
    val id = existing?.id ?: draft.id.ifBlank { UUID.randomUUID().toString() }
    val med = draft.copy(id = id, createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis())
    write("medications.json", medications().filterNot { it.id == id } + med) { it.toJson() }
    return med
  }

  @Synchronized
  fun deleteMedication(id: String) {
    write("medications.json", medications().filterNot { it.id == id }) { it.toJson() }
  }

  /**
   * Data from before the Reminders page: places.json and a photos folder of room and bottle
   * reference photos. A medication's place name is carried into its free-text location
   * ("in the bathroom, in the cabinet above the sink"), then places and photos are deleted.
   * Best effort: if anything fails, nothing is deleted and it runs again on the next read.
   * Family, memories and visits live elsewhere and are not touched.
   */
  private fun migrateLegacy() {
    val placesFile = File(dir, "places.json")
    val photos = File(dir, "photos")
    if (!placesFile.exists() && !photos.exists()) return
    try {
      val places = readJsonArray(placesFile)
      val placeNames =
          (0 until places.length()).associate { i ->
            places.getJSONObject(i).let { it.optString("id") to it.optString("name").trim() }
          }
      val medsFile = File(dir, "medications.json")
      if (medsFile.exists()) {
        val raw = readJsonArray(medsFile)
        val meds =
            (0 until raw.length()).map { i ->
              val o = raw.getJSONObject(i)
              val med = Medication.fromJson(o)
              val room = placeNames[o.optString("storedInPlaceId")]?.takeIf(String::isNotEmpty)
              if (room == null || o.has("storageLocation")) med
              else {
                med.copy(
                    storageLocation =
                        listOfNotNull("in the ${room.lowercase()}", withPreposition(med.storageLocation)).joinToString(", "),
                )
              }
            }
        write("medications.json", meds) { it.toJson() }
      }
      photos.deleteRecursively()
      placesFile.delete()
      File(dir, "places.json.corrupt").delete()
      Log.i("Myst:Store", "removed places and reference photos from an older version")
    } catch (e: JSONException) {
      Log.w("Myst:Store", "legacy cleanup skipped: ${e.javaClass.simpleName}")
    }
  }

  // MARK: - Events

  @Synchronized fun events(): List<Event> = read("events.json", Event::fromJson)

  @Synchronized
  fun log(event: Event) = write("events.json", events() + event) { it.toJson() }

  // MARK: - Visit memory (D18)

  /** When this person was last recognized, oldest first. Times only, no photos. */
  @Synchronized
  fun sightings(personId: String): List<Long> =
      events().filter { it.type == EventType.VISIT && it.personId == personId }.map { it.atMillis }.sorted()

  /**
   * Logs one recognition. A repeat within [VisitMemory.DEDUPE_MS] is not stored, and VISIT events
   * older than [VisitMemory.KEEP_DAYS] are deleted in the same write. [force] is for the demo
   * control, which backfills an earlier sighting.
   */
  @Synchronized
  fun logSighting(personId: String, atMillis: Long, detail: String, force: Boolean = false) {
    val all = events()
    val near =
        all.any {
          it.type == EventType.VISIT &&
              it.personId == personId &&
              kotlin.math.abs(atMillis - it.atMillis) <= VisitMemory.DEDUPE_MS
        }
    if (near && !force) return
    val kept = all.filterNot { VisitMemory.expired(it, atMillis) }
    write("events.json", kept + Event(UUID.randomUUID().toString(), EventType.VISIT, atMillis, personId, detail = detail)) {
      it.toJson()
    }
  }

  /** Consent revoked: forget every sighting of this person. */
  @Synchronized
  fun deleteSightings(personId: String) {
    write("events.json", events().filterNot { it.type == EventType.VISIT && it.personId == personId }) { it.toJson() }
  }

  /** Demo reset: remove every medication and event. */
  @Synchronized
  fun clear() {
    dir.deleteRecursively()
    dir.mkdirs()
  }

  /**
   * Logs a reminder that was spoken. Reminder entries older than [REMINDER_KEEP_DAYS] are dropped
   * in the same write, so the timeline cannot grow without bound.
   */
  @Synchronized
  fun logReminder(event: Event) {
    val cutoff = event.atMillis - REMINDER_KEEP_DAYS * 24 * 60 * 60_000L
    val kept =
        events().filterNot {
          (it.type == EventType.DOSE_REMINDED || it.type == EventType.DOSE_REREMINDED) && it.atMillis < cutoff
        }
    write("events.json", kept + event) { it.toJson() }
  }

  private companion object {
    const val REMINDER_KEEP_DAYS = 30
  }

  private fun <T> read(name: String, parse: (JSONObject) -> T): List<T> =
      readJsonArray(File(dir, name)).let { a -> (0 until a.length()).map { parse(a.getJSONObject(it)) } }

  private fun <T> write(name: String, items: List<T>, toJson: (T) -> JSONObject) =
      writeJsonArray(File(dir, name), JSONArray().apply { items.forEach { put(toJson(it)) } })
}

/**
 * Reads a JSON array file. An unreadable file (for example left empty by a power loss) is moved
 * aside as `.corrupt` and treated as empty, so the app keeps running; nothing is silently deleted.
 */
internal fun readJsonArray(file: File): JSONArray {
  if (!file.exists()) return JSONArray()
  return try {
    JSONArray(file.readText())
  } catch (e: JSONException) {
    Log.w("Myst:Store", "${file.name} unreadable, kept as ${file.name}.corrupt")
    file.renameTo(File(file.parentFile, "${file.name}.corrupt"))
    JSONArray()
  }
}

/** Writes to a temp file, forces it to disk, then swaps it in, so a crash never leaves it empty. */
internal fun writeJsonArray(file: File, array: JSONArray) {
  val tmp = File(file.parentFile, "${file.name}.tmp")
  FileOutputStream(tmp).use { out ->
    out.write(array.toString().toByteArray())
    out.fd.sync()
  }
  tmp.renameTo(file)
}
