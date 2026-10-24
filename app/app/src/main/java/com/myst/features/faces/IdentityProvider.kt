package com.myst.features.faces

import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.records.Person
import com.myst.glasses.Photo
import com.myst.muse.MuseClient
import org.json.JSONArray
import org.json.JSONObject

sealed interface Identity {
  data class Match(val person: Person) : Identity
  /** Not sure, or not enrolled. Never guess a name (R4). */
  data object Unsure : Identity
}

/** The one identity interface (D7). Matching and memory lookup stay separate steps. */
interface IdentityProvider {
  val capability: Capability

  /** Only providers that match faces receive the photo. */
  val usesPhoto: Boolean
    get() = false

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
 * EXPERIMENTAL, UNAPPROVED (D12). Muse Spark compares the live glasses photo with each enrolled
 * person's glasses reference photo. Built only when a human sets myst.allowFaceId=true; Meta's AUP
 * exception was not obtained (D7, Q2) and Q12 is open. Photos are sent once and never stored.
 * Anything short of one confident, known match is Unsure: never guess a name (R4).
 */
class MuseIdentity(
    private val muse: MuseClient,
    private val enrolled: () -> List<Pair<Person, ByteArray>>,
) : IdentityProvider {
  companion object {
    const val THRESHOLD = 0.85

    private const val SYSTEM =
        "You help a person with memory loss recognize family members who consented to be " +
            "recognized. The first image is a live photo. Each later image is a reference photo, " +
            "introduced by its id. If the main face in the live photo is clearly the same person " +
            "as exactly one reference, return that id. If no face is visible, the face is unclear, " +
            "more than one reference could match, or nobody matches, return \"none\". Never guess. " +
            "confidence is your certainty from 0 to 1."

    private val SCHEMA =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("match_id", JSONObject().put("type", "string"))
                    .put("confidence", JSONObject().put("type", "number")),
            )
            .put("required", JSONArray().put("match_id").put("confidence"))
            .put("additionalProperties", false)
  }

  override val capability =
      Capability(
          "Identity (who is in front)",
          Label.EXPERIMENTAL,
          "Muse Spark face match. UNAPPROVED, dev only: Meta's AUP exception not obtained.",
          blockedBy = "D12 (team-accepted risk), D7, Q2, Q12",
      )

  override val usesPhoto = true

  /** Last call's latency and verdict, for the screen. */
  var lastDetail: String? = null
    private set

  override suspend fun identify(photo: Photo?): Identity {
    if (photo == null) return Identity.Unsure.also { lastDetail = "no photo" }
    val refs = enrolled()
    if (refs.isEmpty()) return Identity.Unsure.also { lastDetail = "nobody enrolled with a glasses photo" }
    val parts = buildList {
      add(muse.textPart("Live photo:"))
      add(muse.imagePart(photo.jpeg))
      refs.forEach { (person, jpeg) ->
        add(muse.textPart("Reference id ${person.id}:"))
        add(muse.imagePart(jpeg))
      }
    }
    return when (val r = muse.structured("identify", SYSTEM, parts, "identity", SCHEMA)) {
      is MuseClient.Result.Failed -> Identity.Unsure.also { lastDetail = "Muse ${r.reason} (${r.ms} ms)" }
      is MuseClient.Result.Ok -> {
        val id = r.json.optString("match_id")
        val confidence = r.json.optDouble("confidence", 0.0)
        val person = refs.firstOrNull { it.first.id == id }?.first
        lastDetail = "Muse ${r.ms} ms, confidence ${"%.2f".format(confidence)}"
        if (person != null && confidence >= THRESHOLD) Identity.Match(person) else Identity.Unsure
      }
    }
  }
}
