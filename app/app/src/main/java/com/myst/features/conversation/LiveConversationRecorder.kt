package com.myst.features.conversation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.muse.MuseRealtimeClient
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Streams glasses HFP PCM to Meta Voice Transcribe. Audio is never written to storage or logs. */
class LiveConversationRecorder(private val context: Context, private val apiKey: () -> String?) {
  companion object {
    val capability = Capability(
        "Live conversation transcript",
        Label.EXPERIMENTAL,
        "Consented HFP audio streams to Meta Voice Transcribe; text stays in memory for the session",
        blockedBy = "Real-glasses live session, recap button, and consent flow need device validation",
    )
    private const val SAMPLE_RATE = 16_000
    private const val FRAME_BYTES = SAMPLE_RATE * 2 * 80 / 1_000
  }

  private val requestedStop = AtomicBoolean(false)

  fun stop() { requestedStop.set(true) }

  suspend fun run(
      onReady: () -> Unit,
      /** Return true if a partial transcript contains the stop command. */
      onPartial: (String) -> Boolean,
      /** Return true if a final transcript contains the stop command. */
      onTurn: (String) -> Boolean,
      onFailure: (String) -> Unit,
  ) = withContext(Dispatchers.IO) {
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      onFailure("Microphone permission is needed to record a conversation.")
      return@withContext
    }
    val token = apiKey()?.takeIf(String::isNotBlank)
    if (token == null) {
      onFailure("The Meta transcription key is not available on this phone.")
      return@withContext
    }
    val audio = context.getSystemService(AudioManager::class.java)
    val device = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    if (device == null) {
      onFailure("The glasses microphone is not available.")
      return@withContext
    }

    val oldMode = audio.mode
    val oldDevice = audio.communicationDevice
    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
        .build()
    var focusGranted = false
    var recorder: AudioRecord? = null
    var socket: MuseRealtimeClient? = null
    var eventJob: Job? = null
    var failure: String? = null
    try {
      check(audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        "Audio is busy; pause other playback and try recording again."
      }
      focusGranted = true
      audio.mode = AudioManager.MODE_IN_COMMUNICATION
      check(audio.setCommunicationDevice(device)) { "The glasses microphone route could not start." }
      delay(2_000)
      check(audio.communicationDevice?.id == device.id) { "The glasses microphone route did not settle." }

      val format = AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(SAMPLE_RATE)
          .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
          .build()
      val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
      check(minBytes > 0) { "The glasses microphone does not support 16 kHz audio." }
      recorder = AudioRecord.Builder()
          .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
          .setAudioFormat(format)
          .setBufferSizeInBytes(maxOf(minBytes * 2, FRAME_BYTES * 2))
          .build()
      check(recorder.state == AudioRecord.STATE_INITIALIZED) { "The glasses microphone could not initialize." }
      recorder.startRecording()
      check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "The glasses microphone did not start." }
      check(recorder.routedDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) { "Audio is not coming from the glasses." }

      socket = MuseRealtimeClient.connect(token)
      val connection = socket
      val scope = CoroutineScope(currentCoroutineContext())
      eventJob = scope.launch(Dispatchers.IO) {
        try {
          connection.readEvents { event ->
            when (event.type) {
              "transcript" -> if (!event.json.optBoolean("final") &&
                  onPartial(event.json.optString("transcript"))) requestedStop.set(true)
              "speechComplete" -> {
                val transcript = event.json.optString("transcript").trim()
                if (transcript.isNotEmpty() && onTurn(transcript)) requestedStop.set(true)
              }
            }
          }
        } catch (e: Exception) {
          if (!requestedStop.get()) {
            failure = e.message?.takeIf { it.length <= 100 } ?: "Live transcription stopped unexpectedly."
          }
        } finally {
          requestedStop.set(true)
        }
      }
      if (!requestedStop.get()) onReady()
      val suppressMicUntil = SystemClock.elapsedRealtime() + 2_000L

      val frame = ByteArray(FRAME_BYTES)
      try {
        while (currentCoroutineContext().isActive && !requestedStop.get()) {
          val read = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
          if (read < 0) error("The glasses microphone stopped unexpectedly.")
          if (read > 0) {
            // The spoken start cue is output through the glasses and can bleed into their mic.
            // Send silence for its first two seconds so it is not stored as conversation text.
            if (SystemClock.elapsedRealtime() < suppressMicUntil) frame.fill(0, 0, read)
            connection.sendAudio(frame, read)
          }
        }
      } finally {
        frame.fill(0)
      }
      connection.endInput()
      withTimeoutOrNull(5_000) { eventJob.join() }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      failure = e.message?.takeIf { it.length <= 100 } ?: "Live transcription stopped unexpectedly."
    } finally {
      // Closing the socket unblocks a reader waiting for Meta, including after a phone stop.
      socket?.close()
      eventJob?.cancelAndJoin()
      recorder?.runCatching { stop() }
      recorder?.release()
      audio.clearCommunicationDevice()
      audio.mode = oldMode
      if (oldDevice != null) audio.setCommunicationDevice(oldDevice)
      if (focusGranted) audio.abandonAudioFocusRequest(focusRequest)
    }
    failure?.let(onFailure)
    if (failure == null && !requestedStop.get()) onFailure("Live transcription ended unexpectedly.")
  }
}
