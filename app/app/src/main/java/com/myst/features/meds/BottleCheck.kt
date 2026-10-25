package com.myst.features.meds

import android.util.Log
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.records.Medication
import com.myst.glasses.Photo
import com.myst.muse.MuseClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bottle check (D14, Proposed): two different kinds of evidence must agree before Myst names a
 * bottle. (a) Muse compares the glasses photo with the caregiver's bottle reference photos;
 * (b) a separate Muse call reads the label text. A false "yes" is a stop condition (PRD), so
 * anything short of agreement is [Verdict.Unsure].
 */
class BottleCheck(private val muse: MuseClient) {
  companion object {
    const val MATCH_THRESHOLD = 0.9
    private const val TAG = "Myst:Muse"

    val capability =
        Capability(
            "Bottle check",
            Label.EXPERIMENTAL,
            "Muse photo match + separate label read; both must agree",
            blockedBy = "D14 (Proposed), PRD check 3 (20-photo test)",
        )

    private const val MATCH_SYSTEM =
        "You help a person with memory loss check which of their medicines they are holding. The " +
            "first image is a live photo from their glasses. Each later image is a reference " +
            "photo of one of their own medicine containers (a bottle, box, blister pack, tube or " +
            "package), labelled M1, M2, ... First say whether a container or package is clearly " +
            "visible in the live photo. Then, for every reference, judge whether the item in the " +
            "live photo is the same product as in that reference: compare the packaging design, " +
            "colours, shape and any readable text, ignoring angle and lighting. Give a confidence " +
            "from 0 to 1 for each. Answer only with the JSON."

    private const val READ_SYSTEM =
        "Read the product name printed on the package or label the person is holding in this " +
            "photo, exactly as printed (with strength if shown). It may be a bottle, box, blister " +
            "pack or other package. If no printed name is readable, set label_readable to false " +
            "and leave the name empty. Never guess a name that is not printed. Answer only with " +
            "the JSON."

    private val MATCH_SCHEMA =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("container_visible", JSONObject().put("type", "boolean"))
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
                                            .put("same_bottle", JSONObject().put("type", "boolean"))
                                            .put("confidence", JSONObject().put("type", "number")),
                                    )
                                    .put("required", JSONArray().put("label").put("same_bottle").put("confidence"))
                                    .put("additionalProperties", false),
                            ),
                    ),
            )
            .put("required", JSONArray().put("container_visible").put("references"))
            .put("additionalProperties", false)

    private val READ_SCHEMA =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("label_readable", JSONObject().put("type", "boolean"))
                    .put("medication_name", JSONObject().put("type", "string")),
            )
            .put("required", JSONArray().put("label_readable").put("medication_name"))
            .put("additionalProperties", false)
  }

  sealed interface Verdict {
    /** Both kinds of evidence name this medication. */
    data class Identified(val medication: Medication) : Verdict
    data object Unsure : Verdict
  }

  data class Result(
      val verdict: Verdict,
      /** Photo match: best medication and confidence, or null. */
      val photoMatch: Pair<Medication, Double>?,
      val bottleVisible: Boolean,
      /** Label text as read, or null if unreadable. */
      val labelText: String?,
      /** Medication the label text names, if any. */
      val labelMatch: Medication?,
      val detail: String,
  )

  suspend fun identify(
      photo: Photo,
      meds: List<Pair<Medication, List<ByteArray>>>,
  ): Result = coroutineScope {
    val withPhotos = meds.filter { it.second.isNotEmpty() }
    val labeled = withPhotos.mapIndexed { i, m -> "M${i + 1}" to m }

    val matchCall = async {
      if (labeled.isEmpty()) null
      else
          muse.structured(
              "bottle-match",
              MATCH_SYSTEM,
              buildList {
                add(muse.textPart("Live photo:"))
                add(muse.imagePart(photo.jpeg))
                labeled.forEach { (label, m) ->
                  // Up to 3 references per bottle keeps the request small.
                  m.second.take(3).forEach { jpeg ->
                    add(muse.textPart("Reference $label:"))
                    add(muse.imagePart(jpeg))
                  }
                }
              },
              "bottle_match",
              MATCH_SCHEMA,
              reasoningEffort = "low",
              maxTokens = 1500,
          )
    }
    // The label read sometimes ran out of tokens (finish=length) or found nothing on a first
    // pass; one retry is cheap next to the photo match, which is the slower call.
    val readCall = async {
      suspend fun read() =
          muse.structured(
              "bottle-read",
              READ_SYSTEM,
              listOf(muse.textPart("Live photo:"), muse.imagePart(photo.jpeg)),
              "label_read",
              READ_SCHEMA,
              reasoningEffort = "low",
              maxTokens = 2500,
          )
      val first = read()
      val readable = first is MuseClient.Result.Ok && first.json.optBoolean("label_readable", false)
      if (readable) first else read()
    }

    // (a) Photo match.
    var bottleVisible = false
    var photoYes: List<Pair<Medication, Double>> = emptyList()
    val matchDetail =
        when (val r = matchCall.await()) {
          null -> "no bottle photos enrolled"
          is MuseClient.Result.Failed -> "photo match failed: ${r.reason}"
          is MuseClient.Result.Ok -> {
            bottleVisible = r.json.optBoolean("container_visible", false)
            val refs = r.json.optJSONArray("references") ?: JSONArray()
            // Several reference photos, or several schedules of the same product (same label
            // name), can each say yes; they count as one product. Keep the best per product.
            photoYes =
                (0 until refs.length())
                    .map { refs.getJSONObject(it) }
                    .filter { it.optBoolean("same_bottle") }
                    .mapNotNull { v ->
                      labeled.firstOrNull { it.first == v.optString("label") }
                          ?.let { it.second.first to v.optDouble("confidence", 0.0) }
                    }
                    .groupBy { it.first.labelName.lowercase().replace(Regex("[^a-z0-9]"), "") }
                    .map { (_, list) -> list.maxBy { it.second } }
            "photo match ${r.ms} ms"
          }
        }
    val photoMatch = photoYes.maxByOrNull { it.second }

    // (b) Label read.
    var labelText: String? = null
    val readDetail =
        when (val r = readCall.await()) {
          is MuseClient.Result.Failed -> "label read failed: ${r.reason}"
          is MuseClient.Result.Ok -> {
            if (r.json.optBoolean("label_readable", false)) {
              labelText = r.json.optString("medication_name").trim().takeIf { it.isNotEmpty() }
            }
            "label read ${r.ms} ms"
          }
        }
    val labelMatch = labelText?.let { text -> meds.map { it.first }.firstOrNull { nameMatches(it, text) } }

    val agreed =
        photoYes.singleOrNull()
            ?.takeIf {
              bottleVisible && it.second >= MATCH_THRESHOLD &&
                  labelMatch != null && MedicineSummary.sameProduct(labelMatch, it.first)
            }
            ?.first
    val detail = "$matchDetail, $readDetail"
    Log.i(
        TAG,
        "bottle verdict: visible=$bottleVisible photoYes=${photoYes.size} " +
            "best=${"%.2f".format(photoMatch?.second ?: 0.0)} labelRead=${labelText != null} " +
            "labelMatched=${labelMatch != null} agree=${agreed != null} ($detail)",
    )
    Result(
        verdict = agreed?.let { Verdict.Identified(it) } ?: Verdict.Unsure,
        photoMatch = photoMatch,
        bottleVisible = bottleVisible,
        labelText = labelText,
        labelMatch = labelMatch,
        detail = detail,
    )
  }

  /** The label text names [med] if it contains the first word of the caregiver's label name. */
  private fun nameMatches(med: Medication, labelText: String): Boolean {
    val key = med.labelName.lowercase().split(Regex("[^a-z0-9]+")).firstOrNull { it.length >= 4 } ?: return false
    return labelText.lowercase().contains(key)
  }
}
