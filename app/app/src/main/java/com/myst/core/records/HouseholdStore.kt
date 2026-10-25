package com.myst.core.records

import android.content.Context
import java.io.File
import java.util.UUID
import org.json.JSONArray
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

  private fun <T> read(name: String, parse: (JSONObject) -> T): List<T> {
    val f = File(dir, name)
    if (!f.exists()) return emptyList()
    val a = JSONArray(f.readText())
    return (0 until a.length()).map { parse(a.getJSONObject(it)) }
  }

  private fun <T> write(name: String, items: List<T>, toJson: (T) -> JSONObject) {
    val tmp = File(dir, "$name.tmp")
    tmp.writeText(JSONArray().apply { items.forEach { put(toJson(it)) } }.toString())
    tmp.renameTo(File(dir, name))
  }
}
