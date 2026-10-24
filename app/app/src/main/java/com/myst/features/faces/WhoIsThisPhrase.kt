package com.myst.features.faces

import com.myst.core.Capability
import com.myst.core.Label

/**
 * Trigger phrase for Familiar Faces, heard by DAT Speech on the glasses. Transcripts are only
 * matched here: never stored, logged or shown, since they can contain other people's speech.
 */
object WhoIsThisPhrase {
  val capability =
      Capability(
          "Spoken \"Who is this?\"",
          Label.EXPERIMENTAL,
          "DAT Speech on the glasses; the mic listens only while Listening is on",
          blockedBy = "Speech may need enabling in Wearables Developer Center",
      )

  private val PATTERNS =
      listOf("who is this", "whos this", "who is that", "whos that", "who is it", "whos it")

  fun matches(transcript: String): Boolean {
    val t = transcript.lowercase().replace(Regex("[^a-z ]"), "").replace(Regex("\\s+"), " ")
    return PATTERNS.any { t.contains(it) }
  }
}
