package com.myst.features.faces

import android.os.SystemClock
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.core.guard.ResponseGuard
import com.myst.core.records.Person
import com.myst.core.records.VisitMemory
import com.myst.glasses.CaptureResult
import com.myst.glasses.GlassesGateway
import com.myst.glasses.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WhisperResult(
    val identityLabel: String,
    val spoken: String?,
    val blockedReason: String?,
    val captureMs: Long?,
    val totalMs: Long,
    val note: String?,
    val glassesUpdateRequired: Boolean = false,
)

/**
 * Familiar Faces: trigger -> one glasses photo -> identity -> record lookup -> whisper -> guard ->
 * speak. The photo is dropped right after capture; with MockIdentity it is never looked at.
 */
class FamiliarFaces(
    private val glasses: GlassesGateway,
    private val identity: () -> IdentityProvider,
    private val speaker: Speaker,
    private val people: () -> List<Person>,
    /** D18 visit memory: earlier sightings of a person, and a way to log this one. */
    private val sightings: (String) -> List<Long> = { emptyList() },
    private val recordSighting: (personId: String, atMillis: Long, detail: String) -> Unit = { _, _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
  companion object {
    const val NOT_SURE = "I'm not sure who that is."
  }

  suspend fun whoIsThis(requestPermission: suspend (Permission) -> PermissionStatus): WhisperResult {
    val t0 = SystemClock.elapsedRealtime()
    val capture = glasses.capturePhoto(requestPermission)
    val captureMs = (capture as? CaptureResult.Ok)?.timings?.totalMs
    val note =
        when (capture) {
          is CaptureResult.Ok -> null
          CaptureResult.Paused -> "Glasses paused; continuing without a photo."
          CaptureResult.CapabilityDenied -> "Camera denied for this session; continuing without a photo."
          CaptureResult.PermissionDenied -> "No camera permission; continuing without a photo."
          CaptureResult.GlassesUpdateRequired ->
              "The glasses need a software update (tap Open glasses update); continuing without a photo."
          is CaptureResult.Failed -> "Capture failed (${capture.reason}); continuing without a photo."
        }
    // Only a provider that matches faces gets the photo; otherwise it goes out of scope here.
    val provider = identity()
    val photo = (capture as? CaptureResult.Ok)?.photo
    val who = provider.identify(if (provider.usesPhoto) photo else null)

    val everyone = people()
    val now = clock()
    val (text, source) =
        when (who) {
          is Identity.Match -> {
            // Read the earlier sighting before logging this one, off the main thread.
            val before = withContext(Dispatchers.IO) { sightings(who.person.id) }
            whisperFor(who.person, VisitMemory.previousEncounter(before, now), now) to who.person
          }
          Identity.Unsure -> NOT_SURE to null
        }
    // Only a match is logged: uncertain or silent answers never create a memory of a visit.
    (who as? Identity.Match)?.let { m ->
      withContext(Dispatchers.IO) { recordSighting(m.person.id, now, provider.capability.label.name) }
    }
    val decision = ResponseGuard.check(text, source, everyone)
    val spoken =
        when (decision) {
          is ResponseGuard.Decision.Pass -> decision.line.also(speaker::say).text
          is ResponseGuard.Decision.Block -> null
        }
    return WhisperResult(
        identityLabel =
            provider.capability.label.name +
                ((provider as? MuseIdentity)?.lastDetail?.let { " ($it)" } ?: ""),
        spoken = spoken,
        blockedReason = (decision as? ResponseGuard.Decision.Block)?.reason,
        captureMs = captureMs,
        totalMs = SystemClock.elapsedRealtime() - t0,
        note = note,
        glassesUpdateRequired = capture == CaptureResult.GlassesUpdateRequired,
    )
  }

  /**
   * Name first, then relationship (R2), then when she last saw them (D18), then one
   * caregiver-written memory if there is room under the 20-word limit.
   */
  // The caregiver's first memory is spoken first; later asks rotate through the rest so a repeat
  // "Who is this?" gives her something new to talk about. Resets when the app restarts.
  private val memoryTurn = mutableMapOf<String, Int>()

  private fun nextMemory(person: Person) =
      person.memories.takeIf { it.isNotEmpty() }?.let { list ->
        val i = memoryTurn.getOrDefault(person.id, 0)
        memoryTurn[person.id] = i + 1
        list[i % list.size]
      }

  private fun whisperFor(person: Person, previous: Long?, now: Long): String {
    val relation = person.relationship.trim().lowercase().takeIf { it.isNotEmpty() }
    val base = if (relation != null) "${person.name}, your $relation." else "${person.name}."
    val memory = nextMemory(person)?.text?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
    return VisitMemory.compose(base, VisitMemory.sentence(person.name, previous, now), memory?.let { "$it." })
  }
}
