package com.myst.features.faces

import android.os.SystemClock
import android.util.Log
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
 * speak. The photo is dropped right after identify. Without a provider (face ID not in this build,
 * D12) no photo is taken at all.
 */
class FamiliarFaces(
    private val glasses: GlassesGateway,
    /** Null when this build does not allow face identification (D12). */
    private val identity: () -> IdentityProvider?,
    private val speaker: Speaker,
    private val people: () -> List<Person>,
    /** D18 visit memory: earlier sightings of a person, and a way to log this one. */
    private val sightings: (String) -> List<Long> = { emptyList() },
    private val recordSighting: (personId: String, atMillis: Long, detail: String) -> Unit = { _, _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
  companion object {
    private const val TAG = "Myst:Faces"
    const val NOT_SURE = "I'm not sure who that is."
    const val CANT_CHECK = "I can't check right now."
    const val CANT_SEE = "I can't see right now."
    const val NOT_SET_UP = "Face recognition isn't set up on this phone."

    /** Answers that name nobody; the Home screen shows them as quiet. */
    val NO_NAME_LINES = setOf(NOT_SURE, CANT_CHECK, CANT_SEE, NOT_SET_UP)
  }

  suspend fun whoIsThis(requestPermission: suspend (Permission) -> PermissionStatus): WhisperResult {
    val t0 = SystemClock.elapsedRealtime()
    val provider =
        identity() ?: return plain(NOT_SET_UP, "NOT SET UP", null, t0, "Face recognition is not in this build (D12).")
    val capture = glasses.capturePhoto(requestPermission)
    val captureMs = (capture as? CaptureResult.Ok)?.timings?.totalMs
    val photo =
        (capture as? CaptureResult.Ok)?.photo
            ?: return plain(
                CANT_SEE,
                provider.capability.label.name,
                null,
                t0,
                captureNote(capture),
                glassesUpdateRequired = capture == CaptureResult.GlassesUpdateRequired,
            )
    val who = provider.identify(photo)
    val label =
        provider.capability.label.name + ((provider as? MuseIdentity)?.lastDetail?.let { " ($it)" } ?: "")
    val match =
        when (who) {
          is Identity.Match -> who
          Identity.Unsure -> return plain(NOT_SURE, label, captureMs, t0, null)
          is Identity.Unavailable ->
              return plain(CANT_CHECK, label, captureMs, t0, "Muse gave no answer (${who.reason}).")
        }

    val everyone = people()
    val now = clock()
    // Read the earlier sighting before logging this one, off the main thread.
    val before = withContext(Dispatchers.IO) { sightings(match.person.id) }
    // Only a match is logged: uncertain or failed answers never create a memory of a visit.
    withContext(Dispatchers.IO) { recordSighting(match.person.id, now, provider.capability.label.name) }

    // Longest line first; each shorter one drops a part. A match is never answered with silence.
    val candidates = whisperCandidates(match.person, VisitMemory.previousEncounter(before, now), now)
    var firstBlock: String? = null
    for ((level, text) in candidates.withIndex()) {
      when (val d = ResponseGuard.check(text, match.person, everyone)) {
        is ResponseGuard.Decision.Pass -> {
          // Which fallback level was used, never the line itself.
          Log.i(TAG, "whisper level $level of ${candidates.size - 1}")
          speaker.say(d.line)
          return WhisperResult(label, d.line.text, firstBlock, captureMs, SystemClock.elapsedRealtime() - t0, null)
        }
        is ResponseGuard.Decision.Block -> if (firstBlock == null) firstBlock = d.reason
      }
    }
    Log.w(TAG, "whisper: no level passed the guard; saying not sure")
    return plain(NOT_SURE, label, captureMs, t0, "The whisper was blocked ($firstBlock).", blocked = firstBlock)
  }

  /** A line that names nobody, through the guard like every other spoken line. */
  private fun plain(
      text: String,
      label: String,
      captureMs: Long?,
      t0: Long,
      note: String?,
      glassesUpdateRequired: Boolean = false,
      blocked: String? = null,
  ): WhisperResult {
    val decision = ResponseGuard.check(text, null, people())
    val spoken = (decision as? ResponseGuard.Decision.Pass)?.line?.also(speaker::say)?.text
    return WhisperResult(
        identityLabel = label,
        spoken = spoken,
        blockedReason = blocked ?: (decision as? ResponseGuard.Decision.Block)?.reason,
        captureMs = captureMs,
        totalMs = SystemClock.elapsedRealtime() - t0,
        note = note,
        glassesUpdateRequired = glassesUpdateRequired,
    )
  }

  private fun captureNote(capture: CaptureResult): String? =
      when (capture) {
        is CaptureResult.Ok -> null
        CaptureResult.Paused -> "Glasses paused; no photo taken."
        CaptureResult.CapabilityDenied -> "Camera denied for this session; no photo taken."
        CaptureResult.PermissionDenied -> "No camera permission; no photo taken."
        CaptureResult.GlassesUpdateRequired -> "The glasses need a software update (tap Open glasses update)."
        is CaptureResult.Failed -> "Capture failed (${capture.reason}); no photo taken."
      }

  // The caregiver's first memory is spoken first; later asks rotate through the rest so a repeat
  // "Who is this?" gives her something new to talk about. Resets when the app restarts.
  private val memoryTurn = mutableMapOf<String, Int>()

  private fun nextMemory(person: Person) =
      person.memories.takeIf { it.isNotEmpty() }?.let { list ->
        val i = memoryTurn.getOrDefault(person.id, 0)
        memoryTurn[person.id] = i + 1
        list[i % list.size]
      }

  /**
   * Hedged name first, then relationship (R2), then when she last saw them (D18), then one
   * caregiver-written memory if there is room under the 20-word limit. Longest first: the full
   * line, then without the memory, then the name and relationship alone.
   */
  private fun whisperCandidates(person: Person, previous: Long?, now: Long): List<String> {
    val relation = person.relationship.trim().lowercase().takeIf { it.isNotEmpty() }
    val base =
        if (relation != null) "I think that's ${person.name}, your $relation." else "I think that's ${person.name}."
    val visit = VisitMemory.sentence(person.name, previous, now)
    val memory = nextMemory(person)?.text?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }?.let { "$it." }
    return listOf(VisitMemory.compose(base, visit, memory), VisitMemory.compose(base, visit, null), base).distinct()
  }
}
