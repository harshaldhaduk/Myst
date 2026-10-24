package com.myst.core

/** Every capability Myst shows carries one of these, in code and on screen (AGENTS.md: truth labels). */
enum class Label {
  /** A real Meta capability on real hardware. */
  LIVE,
  /** Scripted or simulated; never presented as working. */
  MOCK,
  /** Real, but Meta marks it experimental (test use only, DAT Terms 2.8). */
  EXPERIMENTAL,
}

/** [blockedBy] names the open question or approval that keeps this from being LIVE, if any. */
data class Capability(
    val name: String,
    val label: Label,
    val note: String,
    val blockedBy: String? = null,
)
