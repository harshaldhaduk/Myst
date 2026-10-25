package com.myst.glasses

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * A short soft tone the moment a trigger fires, so the wearer knows Myst heard her while the photo
 * and Muse call run. Android AudioTrack on media output (host OS, not Meta), which reaches the
 * glasses over A2DP like the TTS. A sine with a gentle fade in and out, no sharp click.
 */
class Earcon {
  companion object {
    private const val TAG = "Myst:Earcon"
    const val DURATION_MS = 150L
    private const val SAMPLE_RATE = 24_000
    private const val FREQUENCY_HZ = 660.0
    private const val VOLUME = 0.25
    private const val FADE_MS = 30
    /** Time after the tone before the track is released. */
    private const val RELEASE_SLACK_MS = 100L
  }

  private val samples: ShortArray by lazy {
    val n = (SAMPLE_RATE * DURATION_MS / 1000).toInt()
    val fade = SAMPLE_RATE * FADE_MS / 1000
    ShortArray(n) { i ->
      val envelope = minOf(1.0, i.toDouble() / fade, (n - 1 - i).toDouble() / fade)
      (sin(2 * PI * FREQUENCY_HZ * i / SAMPLE_RATE) * envelope * VOLUME * Short.MAX_VALUE).toInt().toShort()
    }
  }

  private val main = Handler(Looper.getMainLooper())

  /** Fire and forget. A failure only costs the tone, never the answer. */
  fun play() {
    try {
      val track =
          AudioTrack.Builder()
              .setAudioAttributes(
                  AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_MEDIA)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                      .build(),
              )
              .setAudioFormat(
                  AudioFormat.Builder()
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .setSampleRate(SAMPLE_RATE)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                      .build(),
              )
              .setTransferMode(AudioTrack.MODE_STATIC)
              .setBufferSizeInBytes(samples.size * 2)
              .build()
      track.write(samples, 0, samples.size)
      track.play()
      main.postDelayed({ runCatching { track.release() } }, DURATION_MS + RELEASE_SLACK_MS)
    } catch (e: Exception) {
      Log.w(TAG, "earcon failed: ${e.javaClass.simpleName}")
    }
  }
}
