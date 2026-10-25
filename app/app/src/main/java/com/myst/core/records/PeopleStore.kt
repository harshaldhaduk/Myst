package com.myst.core.records

import android.content.Context
import java.io.File
import java.util.UUID
import org.json.JSONArray

/**
 * Enrolled family, stored in app-private storage on this phone only (Live, app logic).
 *
 * Not a cloud store: identity data stays on the phone (AGENTS.md), D5 rules out a cloud backend,
 * and the glasses hold no app data. Files live under noBackupFilesDir so Android backup never
 * copies them off the phone. Revoking deletes the record and its photo.
 */
class PeopleStore(context: Context) {
  private val dir = File(context.noBackupFilesDir, "people").apply { mkdirs() }
  private val index = File(dir, "people.json")

  @Synchronized
  fun all(): List<Person> {
    val arr = readJsonArray(index)
    return (0 until arr.length()).map { Person.fromJson(arr.getJSONObject(it)) }
  }

  /** D30, called only in debug builds: preserve existing enrollment and caregiver memories. */
  @Synchronized
  fun seedDemoMemories(): List<Person> {
    val people = all()
    val seeded = people.map(DemoMemories::seed)
    if (seeded != people) write(seeded)
    return seeded
  }

  @Synchronized
  fun enroll(
      name: String,
      relationship: String,
      consentRecordedBy: String,
      photoJpeg: ByteArray?,
      photoSource: PhotoSource?,
      memories: List<Memory> = emptyList(),
  ): Person {
    val id = UUID.randomUUID().toString()
    val photoFile = photoJpeg?.let { bytes -> "$id.jpg".also { File(dir, it).writeBytes(bytes) } }
    val now = System.currentTimeMillis()
    val person =
        Person(
            id = id,
            name = name.trim(),
            relationship = relationship.trim(),
            consent = Consent(consentRecordedBy.trim(), now),
            photoFile = photoFile,
            photoSource = photoJpeg?.let { photoSource },
            memories = memories,
            createdAtMillis = now,
        )
    write(all() + person)
    return person
  }

  /** Consent withdrawn: remove the record and its photo. */
  @Synchronized
  fun revoke(id: String) {
    val people = all()
    people.firstOrNull { it.id == id }?.photoFile?.let { File(dir, it).delete() }
    write(people.filterNot { it.id == id })
  }

  // MARK: - Memories (R2, R14): caregiver-written, attributed, and the only source for spoken memories.

  /** Appends one memory and writes the whole record back. Null if the person no longer exists. */
  @Synchronized
  fun addMemory(personId: String, memory: Memory): Person? =
      update(personId) { it.withMemory(memory) }

  /** Deletes the memory at [index]. An out-of-range index changes nothing. */
  @Synchronized
  fun removeMemory(personId: String, index: Int): Person? =
      update(personId) { it.withoutMemory(index) }

  /** The first memory is spoken first (then Myst rotates); this moves [index] to the front. */
  @Synchronized
  fun makeFirstMemory(personId: String, index: Int): Person? =
      update(personId) { it.withMemoryFirst(index) }

  private fun update(id: String, change: (Person) -> Person): Person? {
    val people = all()
    val old = people.firstOrNull { it.id == id } ?: return null
    val updated = change(old)
    write(people.map { if (it.id == id) updated else it })
    return updated
  }

  fun photo(person: Person): File? = person.photoFile?.let { File(dir, it) }?.takeIf { it.exists() }

  private fun write(people: List<Person>) {
    writeJsonArray(index, JSONArray().apply { people.forEach { put(it.toJson()) } })
  }
}
