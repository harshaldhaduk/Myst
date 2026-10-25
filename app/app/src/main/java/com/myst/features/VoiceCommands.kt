package com.myst.features

import com.myst.core.Capability
import com.myst.core.Label

/**
 * Spoken commands heard by DAT Speech on the glasses. Transcripts are only matched here: never
 * stored, logged or shown, since they can contain other people's speech.
 */
object VoiceCommands {
  enum class Command {
    /** Familiar Faces. */
    WHO_IS_THIS,
    /** Starts an opted-in live conversation transcript. */
    RECORD_CONVERSATION,
  }

  val capability =
      Capability(
          "Voice: glasses commands",
          Label.EXPERIMENTAL,
          "DAT Speech recognizes \"Who am I looking at?\" and \"Start recording\" while Myst is on",
          blockedBy = "Speech may need enabling in Wearables Developer Center",
      )

  private val PATTERNS =
      mapOf(
          Command.WHO_IS_THIS to
              listOf("who am i looking at", "whom am i looking at"),
          Command.RECORD_CONVERSATION to listOf("start recording"),
      )

  /** The command in [transcript], or null. */
  fun match(transcript: String): Command? {
    val t = transcript.lowercase().replace(Regex("[^a-z ]"), "").replace(Regex("\\s+"), " ")
    return PATTERNS.entries.firstOrNull { (_, phrases) -> phrases.any { t.contains(it) } }?.key
  }
}
