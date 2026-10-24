package com.myst.core.guard

import com.myst.core.records.Person

/**
 * Every spoken line passes through here (AGENTS.md: never quiz or correct, never invent names).
 * A [GuardedLine] can only be made by [check], so the speaker cannot say an unchecked line.
 */
object ResponseGuard {
  const val MAX_WORDS = 20

  private val BANNED =
      listOf("remember", "forgot", "forget", "wrong", "don't you know", "do you know", "recognize me")

  class GuardedLine private constructor(val text: String) {
    internal companion object {
      fun of(text: String) = GuardedLine(text)
    }
  }

  sealed interface Decision {
    data class Pass(val line: GuardedLine) : Decision
    data class Block(val reason: String) : Decision
  }

  /**
   * [source] is the record the line is about (null for lines naming nobody). [everyone] is the
   * enrolled family: naming any of them other than [source] means the name has no source record.
   */
  fun check(text: String, source: Person?, everyone: List<Person>): Decision {
    val clean = text.trim()
    if (clean.isEmpty()) return Decision.Block("empty")
    val words = clean.split(Regex("\\s+"))
    if (words.size > MAX_WORDS) return Decision.Block("${words.size} words, max $MAX_WORDS")
    val lower = clean.lowercase()
    BANNED.firstOrNull { lower.contains(it) }?.let { return Decision.Block("banned phrase: \"$it\"") }
    if (clean.endsWith("?")) return Decision.Block("questions can quiz the wearer")
    everyone
        .filter { it.id != source?.id }
        .firstOrNull { other -> Regex("\\b${Regex.escape(other.name.lowercase())}\\b").containsMatchIn(lower) }
        ?.let { return Decision.Block("names someone without a source record") }
    return Decision.Pass(GuardedLine.of(clean))
  }
}
