package com.myst.glasses

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.myst.core.Capability
import com.myst.core.Label

/**
 * Experimental trigger through Bluetooth media buttons (Android, not a Meta API). Our phone has
 * received MEDIA_NEXT from the glasses, so handle track controls as well as play/pause. A single
 * touchpad tap during a DAT session is reserved for pause/resume and never reaches the app;
 * double-tap is the candidate gesture to verify on the actual glasses. This takes media controls
 * from music apps while enabled.
 */
class GlassesTapTrigger(private val context: Context, private val onTap: () -> Unit) {
  companion object {
    private const val TAG = "Myst:Tap"
    private const val DEBOUNCE_MS = 2_000L

    val capability =
        Capability(
            "Double-tap the glasses",
            Label.EXPERIMENTAL,
            "Bluetooth media-button trigger; single tap is reserved by DAT for pause/resume",
            blockedBy = "Double-tap recognition flow still needs verification on the real glasses",
        )

    private val TAP_KEYS =
        setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )
  }

  private var session: MediaSession? = null
  private var lastTap = 0L

  val isOn: Boolean
    get() = session != null

  fun start() {
    if (session != null) return
    session =
        MediaSession(context, "MystTap").apply {
          setCallback(
              object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                  // The typed overload is API 33; minSdk is 31.
                  val event =
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                      } else {
                        @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                      }
                  if (event == null || event.keyCode !in TAP_KEYS) return super.onMediaButtonEvent(intent)
                  if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) tap("key ${event.keyCode}")
                  return true
                }

                // Some routes deliver play/pause as transport calls instead of key events.
                override fun onPlay() = tap("onPlay")

                override fun onPause() = tap("onPause")

                override fun onSkipToNext() = tap("onSkipToNext")

                override fun onSkipToPrevious() = tap("onSkipToPrevious")
              },
          )
          setPlaybackState(
              PlaybackState.Builder()
                  .setActions(
                      PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                          PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                          PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                  )
                  .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                  .build(),
          )
          isActive = true
        }
    // Android sends media buttons to the app that played audio last, so play a moment of silence.
    playSilence()
    Log.i(TAG, "listening for glasses taps")
  }

  fun stop() {
    session?.run {
      isActive = false
      release()
    }
    session = null
  }

  private fun tap(source: String) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastTap < DEBOUNCE_MS) return
    lastTap = now
    Log.i(TAG, "tap ($source)")
    onTap()
  }

  private fun playSilence() {
    val rate = 16_000
    val samples = ShortArray(rate / 5) // 200 ms
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
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
    runCatching {
          track.write(samples, 0, samples.size)
          track.setNotificationMarkerPosition(samples.size)
          track.setPlaybackPositionUpdateListener(
              object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = t.release()

                override fun onPeriodicNotification(t: AudioTrack) = Unit
              },
          )
          track.play()
        }
        .onFailure {
          Log.w(TAG, "silence playback failed: ${it.message}")
          track.release()
        }
  }
}
