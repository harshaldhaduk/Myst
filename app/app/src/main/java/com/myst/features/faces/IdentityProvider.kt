package com.myst.features.faces

import android.util.Log
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.records.Person
import com.myst.glasses.Photo
import com.myst.muse.MuseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface Identity {
  data class Match(val person: Person) : Identity
  /** Muse looked and is not sure, saw no face, or nobody is enrolled. Never guess a name (R4). */
  data object Unsure : Identity
  /** No verdict at all: no key, network, auth, timeout or an unusable answer. Not "unsure". */
  data class Unavailable(val reason: String) : Identity
}

/**
 * The one identity interface (D7). Matching and memory lookup stay separate steps. MuseIdentity is
 * the only implementation; a build without face ID has no provider at all.
 */
interface IdentityProvider {
  val capability: Capability

  suspend fun identify(photo: Photo): Identity
}

/**
 * EXPERIMENTAL, UNAPPROVED (D12). Muse Spark compares the live glasses photo with each enrolled
 * person's glasses reference photo. Built only when a human sets myst.allowFaceId=true; Meta's AUP
 * exception was not obtained (D7, Q2) and Q12 is open. Photos are sent once and never stored.
 * Anything short of one confident, known match is Unsure: never guess a name (R4). A call that
 * gives no verdict (no key, network, auth) is Unavailable, so the wearer hears it could not check.
 */
class MuseIdentity(
    private val muse: MuseClient,
    /** Enrolled reference photos. Reads files, so it is called on Dispatchers.IO. */
    private val enrolled: () -> List<Pair<Person, ByteArray>>,
) : IdentityProvider {
  companion object {
    const val THRESHOLD = 0.85

    private const val SYSTEM =
        "This is an accessibility aid for a person with memory loss. Every reference photo shows " +
            "a family member who consented to be recognized, and the wearer asked who is in front " +
            "of them. The first image is a live photo from the wearer's glasses. Each later image " +
            "is a reference photo with a label (R1, R2, ...). First say whether a person's face is " +
            "visible in the live photo. Then, for every reference, judge whether the main person " +
            "in the live photo is the same person as in that reference, comparing facial features " +
            "and ignoring differences in angle, lighting, expression and clothing. Give a " +
            "confidence from 0 to 1 for each. Answer only with the JSON."

    private val SCHEMA =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("face_visible", JSONObject().put("type", "boolean"))
                    .put(
                        "references",
                        JSONObject()
                            .put("type", "array")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("label", JSONObject().put("type", "string"))
                                            .put("same_person", JSONObject().put("type", "boolean"))
                                            .put("confidence", JSONObject().put("type", "number")),
                                    )
                                    .put(
                                        "required",
                                        JSONArray().put("label").put("same_person").put("confidence"),
                                    )
                                    .put("additionalProperties", false),
                            ),
                    ),
            )
            .put("required", JSONArray().put("face_visible").put("references"))
            .put("additionalProperties", false)
  }

  override val capability =
      Capability(
          "Identity (who is in front)",
          Label.EXPERIMENTAL,
          "Muse Spark face match. UNAPPROVED, dev only: Meta's AUP exception not obtained.",
          blockedBy = "D12 (team-accepted risk), D7, Q2, Q12",
      )

  /** Last call's latency and verdict, for the screen. */
  var lastDetail: String? = null
    private set

  override suspend fun identify(photo: Photo): Identity {
    val refs = withContext(Dispatchers.IO) { enrolled() }
    if (refs.isEmpty()) return Identity.Unsure.also { lastDetail = "nobody enrolled with a glasses photo" }
    // Short labels instead of record ids: names and ids never go to Muse.
    val labeled = refs.mapIndexed { i, ref -> "R${i + 1}" to ref }
    val parts = buildList {
      add(muse.textPart("Live photo:"))
      add(muse.imagePart(photo.jpeg))
      labeled.forEach { (label, ref) ->
        add(muse.textPart("Reference $label:"))
        add(muse.imagePart(ref.second))
      }
    }
    val r =
        muse.structured(
            "identify", SYSTEM, parts, "identity", SCHEMA, reasoningEffort = "low", maxTokens = 1500,
        )
    return when (r) {
      is MuseClient.Result.Failed ->
          Identity.Unavailable(r.kind.name).also { lastDetail = "Muse ${r.reason} (${r.ms} ms)" }
      is MuseClient.Result.Ok -> {
        val faceVisible = r.json.optBoolean("face_visible", false)
        val verdicts = r.json.optJSONArray("references") ?: JSONArray()
        val yes =
            (0 until verdicts.length())
                .map { verdicts.getJSONObject(it) }
                .filter { it.optBoolean("same_person") }
                .mapNotNull { v ->
                  labeled.firstOrNull { it.first == v.optString("label") }
                      ?.let { it.second.first to v.optDouble("confidence", 0.0) }
                }
        val best = yes.maxOfOrNull { it.second } ?: 0.0
        // Exactly one confident "same person", with a face in view. Anything else is unsure.
        val match = yes.singleOrNull()?.takeIf { faceVisible && it.second >= THRESHOLD }?.first
        lastDetail =
            "Muse ${r.ms} ms, face ${if (faceVisible) "seen" else "not seen"}, " +
                "${yes.size} of ${labeled.size} said same person, best ${"%.2f".format(best)}"
        Log.i("Myst:Muse", "identify verdict: $lastDetail -> ${if (match != null) "match" else "unsure"}")
        match?.let { Identity.Match(it) } ?: Identity.Unsure
      }
    }
  }
}
