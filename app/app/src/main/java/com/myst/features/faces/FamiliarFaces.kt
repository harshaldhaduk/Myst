package com.myst.features.faces

import android.os.SystemClock
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.core.guard.ResponseGuard
import com.myst.core.records.Person
import com.myst.glasses.CaptureResult
import com.myst.glasses.GlassesGateway
import com.myst.glasses.Speaker

data class WhisperResult(
    val identityLabel: String,
    val spoken: String?,
    val blockedReason: String?,
    val captureMs: Long?,
    val totalMs: Long,
    val note: String?,
)

/**
 * Familiar Faces: trigger -> one glasses photo -> identity -> record lookup -> whisper -> guard ->
 * speak. The photo is dropped right after capture; with MockIdentity it is never looked at.
 */
class FamiliarFaces(
    private val glasses: GlassesGateway,
    private val identity: IdentityProvider,
    private val speaker: Speaker,
    private val people: () -> List<Person>,
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
          is CaptureResult.Failed -> "Capture failed (${capture.reason}); continuing without a photo."
        }
    // MockIdentity never uses the photo, so none is passed on and it goes out of scope here.
    val who = identity.identify(photo = null)

    val everyone = people()
    val (text, source) =
        when (who) {
          is Identity.Match -> whisperFor(who.person) to who.person
          Identity.Unsure -> NOT_SURE to null
        }
    val decision = ResponseGuard.check(text, source, everyone)
    val spoken =
        when (decision) {
          is ResponseGuard.Decision.Pass -> decision.line.also(speaker::say).text
          is ResponseGuard.Decision.Block -> null
        }
    return WhisperResult(
        identityLabel = identity.capability.label.name,
        spoken = spoken,
        blockedReason = (decision as? ResponseGuard.Decision.Block)?.reason,
        captureMs = captureMs,
        totalMs = SystemClock.elapsedRealtime() - t0,
        note = note,
    )
  }

  /** Name first, then relationship, then one caregiver-written memory if there is one (R2). */
  private fun whisperFor(person: Person): String {
    val relation = person.relationship.trim().lowercase().takeIf { it.isNotEmpty() }
    val base = if (relation != null) "${person.name}, your $relation." else "${person.name}."
    val memory = person.memories.firstOrNull()?.text?.trim()?.trimEnd('.')
    return if (memory.isNullOrEmpty()) base else "$base $memory."
  }
}
