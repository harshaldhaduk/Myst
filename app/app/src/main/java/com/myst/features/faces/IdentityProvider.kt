package com.myst.features.faces

import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.records.Person
import com.myst.glasses.Photo

sealed interface Identity {
  data class Match(val person: Person) : Identity
  /** Not sure, or not enrolled. Never guess a name (R4). */
  data object Unsure : Identity
}

/** The one identity interface (D7). Matching and memory lookup stay separate steps. */
interface IdentityProvider {
  val capability: Capability

  suspend fun identify(photo: Photo?): Identity
}

/**
 * MOCK. The operator picks who is in front of the wearer in Demo Controls. It never looks at the
 * photo; callers pass null so no face is used at all.
 */
class MockIdentity(private val picked: () -> Person?) : IdentityProvider {
  override val capability =
      Capability(
          "Identity (who is in front)",
          Label.MOCK,
          "Operator picks the person. No face matching runs.",
          blockedBy = "D7 (Meta approval), Q2, Q12",
      )

  override suspend fun identify(photo: Photo?): Identity =
      picked()?.let { Identity.Match(it) } ?: Identity.Unsure
}

/**
 * Not built. A Muse Spark comparison of the live photo against enrolled photos would be face
 * identification under Meta's Wearables AUP, so it waits for written approval (D7) and an answer
 * on sending photos of people to Muse (Q12). It sends nothing and always answers Unsure.
 */
class MuseIdentity : IdentityProvider {
  override val capability =
      Capability(
          "Face match (Muse)",
          Label.MOCK,
          "Disabled. Sends nothing until approval is recorded in hub/DECISIONS.md.",
          blockedBy = "D7, Q2, Q12",
      )

  override suspend fun identify(photo: Photo?): Identity = Identity.Unsure
}
