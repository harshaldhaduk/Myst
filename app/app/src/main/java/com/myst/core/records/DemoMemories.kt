package com.myst.core.records

/** User-requested fictional demo context (D30), never evidence of a visit or conversation. */
object DemoMemories {
  const val AUTHOR = "Demo seed (fictional)"
  const val SOURCE_NOTE = "Scripted demo memory; not a recorded conversation."

  /** Only enrich an existing enrollment. The persisted marker respects deletion on later starts. */
  fun seed(person: Person): Person {
    if (person.demoMemorySeeded) return person
    val text =
        when (person.name.filter(Char::isLetterOrDigit).lowercase()) {
          "krishna" -> "He brought you coffee this morning. His favorite food is dosa."
          "rj" -> "You attended Hack Texas together. He just got a new dog."
          else -> return person
        }
    return person.copy(
        memories = listOf(Memory(text, AUTHOR)) + person.memories,
        demoMemorySeeded = true,
    )
  }

  fun isDemo(memory: Memory): Boolean = memory.author == AUTHOR

  /**
   * Keep both parts of the requested context together, ahead of the generated sighting timestamp.
   * If a long relationship does not fit, try the name and context before the name-only fallback.
   * The caller still checks every candidate with ResponseGuard before speaking.
   */
  fun whisperCandidates(person: Person, base: String): List<String>? {
    val memory = person.memories.firstOrNull(::isDemo) ?: return null
    return listOf("$base ${memory.text}", "I think that's ${person.name}. ${memory.text}", base).distinct()
  }

  fun noteFor(person: Person, spoken: String): String? =
      SOURCE_NOTE.takeIf { person.memories.any { isDemo(it) && spoken.contains(it.text) } }
}
