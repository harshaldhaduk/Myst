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
 * Places, medications and timeline events, in app-private noBackupFilesDir like [PeopleStore].
 * Photos are reference images taken with the glasses; deleting a record deletes its photos.
 */
class HouseholdStore(context: Context) {
  private val dir = File(context.noBackupFilesDir, "household").apply { mkdirs() }
  private val photos = File(dir, "photos").apply { mkdirs() }

  // MARK: - Places

  @Synchronized fun places(): List<Place> = read("places.json", Place::fromJson)

  @Synchronized
  fun savePlace(id: String?, name: String, note: String, newPhotos: List<ByteArray>): Place {
    val existing = places().firstOrNull { it.id == id }
    val placeId = existing?.id ?: UUID.randomUUID().toString()
    val place =
        Place(
            id = placeId,
            name = name.trim(),
            note = note.trim(),
            photoFiles = (existing?.photoFiles ?: emptyList()) + writePhotos(placeId, newPhotos),
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        )
    write("places.json", places().filterNot { it.id == placeId } + place) { it.toJson() }
    return place
  }

  @Synchronized
  fun deletePlace(id: String) {
    places().firstOrNull { it.id == id }?.photoFiles?.forEach { File(photos, it).delete() }
    write("places.json", places().filterNot { it.id == id }) { it.toJson() }
  }

  // MARK: - Medications

  @Synchronized fun medications(): List<Medication> = read("medications.json", Medication::fromJson)

  /** [draft]'s id is kept when editing; pass newPhotos to add bottle reference photos. */
  @Synchronized
  fun saveMedication(draft: Medication, newPhotos: List<ByteArray>): Medication {
    val existing = medications().firstOrNull { it.id == draft.id }
    val id = existing?.id ?: draft.id.ifBlank { UUID.randomUUID().toString() }
    val med =
        draft.copy(
            id = id,
            bottlePhotoFiles = (existing?.bottlePhotoFiles ?: emptyList()) + writePhotos(id, newPhotos),
            createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        )
    write("medications.json", medications().filterNot { it.id == id } + med) { it.toJson() }
    return med
  }

  @Synchronized
  fun deleteMedication(id: String) {
    medications().firstOrNull { it.id == id }?.bottlePhotoFiles?.forEach { File(photos, it).delete() }
    write("medications.json", medications().filterNot { it.id == id }) { it.toJson() }
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

  // MARK: - Photos

  fun photo(file: String): File? = File(photos, file).takeIf { it.exists() }

  /** Demo reset: remove every place, medication, event and their photos. */
  @Synchronized
  fun clear() {
    dir.deleteRecursively()
    dir.mkdirs()
    photos.mkdirs()
  }

  private fun writePhotos(ownerId: String, jpegs: List<ByteArray>): List<String> =
      jpegs.map { bytes ->
        "$ownerId-${UUID.randomUUID()}.jpg".also { File(photos, it).writeBytes(bytes) }
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
