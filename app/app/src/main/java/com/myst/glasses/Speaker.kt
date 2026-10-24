package com.myst.glasses

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.guard.ResponseGuard.GuardedLine
import java.util.Locale

interface Speaker {
  val capability: Capability

  /** Queued, one line at a time. Accepts only lines that passed ResponseGuard. */
  fun say(line: GuardedLine)

  fun interrupt()

  fun shutdown()
}

/**
 * Android's built-in TextToSpeech. Meta has no TTS API, so this is the host OS, not Meta (Q11).
 * Plain media audio, which Android routes to the glasses over Bluetooth A2DP when they are connected.
 */
class AndroidTtsSpeaker(context: Context) : Speaker {
  override val capability =
      Capability(
          "Speech out",
          Label.LIVE,
          "Android OS TTS, played on the glasses over Bluetooth (not a Meta API)",
          blockedBy = "Q11 (is OS TTS acceptable under D5?)",
      )

  private var ready = false
  private val pending = mutableListOf<String>()
  private val tts: TextToSpeech =
      TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          ready = true
          pending.forEach(::enqueue)
          pending.clear()
        }
      }

  init {
    tts.setSpeechRate(0.85f)
    tts.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
    )
  }

  override fun say(line: GuardedLine) {
    if (ready) enqueue(line.text) else pending += line.text
  }

  private fun enqueue(text: String) {
    tts.language = Locale.US
    tts.speak(text, TextToSpeech.QUEUE_ADD, null, "myst-${System.nanoTime()}")
  }

  override fun interrupt() {
    pending.clear()
    tts.stop()
  }

  override fun shutdown() = tts.shutdown()
}
