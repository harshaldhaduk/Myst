package com.myst.features.conversation

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.myst.glasses.CaptureResult
import com.myst.glasses.DatGlassesGateway
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.myst.muse.MuseClient
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One consented real-glasses feasibility run. All audio and transcript data stays in RAM. */
class ConversationSpike(private val context: Context, private val key: () -> String?) {
  data class Result(val report: String, val transcript: String)
  private class WipeableBuffer(size: Int) : ByteArrayOutputStream(size) {
    fun wipe() { buf.fill(0); reset() }
  }

  suspend fun run(
      gateway: DatGlassesGateway,
      permission: suspend (Permission) -> PermissionStatus,
      muse: MuseClient,
      onRecording: () -> Unit,
  ): Result = withContext(Dispatchers.IO) {
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      return@withContext Result("Microphone permission was not granted.", "")
    }
    val audio = context.getSystemService(AudioManager::class.java)
    val device = audio.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        ?: return@withContext Result("No Bluetooth HFP communication device available.", "")
    val oldMode = audio.mode
    var recorder: AudioRecord? = null
    val pcm = WipeableBuffer(30 * 16_000 * 2)
    val chunk = ByteArray(4096)
    var wavBytes = ByteArray(0)
    try {
      audio.mode = AudioManager.MODE_IN_COMMUNICATION
      if (!audio.setCommunicationDevice(device)) return@withContext Result("HFP route request failed.", "")
      delay(2_000) // Meta's route-settle guidance before camera use.
      val routed = audio.communicationDevice?.id == device.id
      val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16_000)
          .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
      val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
      if (min <= 0) return@withContext Result("AudioRecord does not support 16 kHz mono PCM ($min).", "")
      recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
          .setAudioFormat(format).setBufferSizeInBytes(maxOf(min * 2, 8192)).build()
      if (recorder.state != AudioRecord.STATE_INITIALIZED) return@withContext Result("AudioRecord did not initialize.", "")
      recorder.startRecording()
      if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) return@withContext Result("AudioRecord did not start.", "")
      onRecording()
      val started = SystemClock.elapsedRealtime()
      var photo: CaptureResult? = null
      var nonzero = 0L
      while (SystemClock.elapsedRealtime() - started < 30_000) {
        val n = recorder.read(chunk, 0, chunk.size)
        if (n > 0) {
          pcm.write(chunk, 0, n)
          for (i in 0 until n) if (chunk[i].toInt() != 0) nonzero++
        }
        if (photo == null && SystemClock.elapsedRealtime() - started >= 10_000) {
          photo = gateway.capturePhoto(permission)
        }
      }
      recorder.stop()
      val bytes = pcm.toByteArray()
      val photoText = when (photo) {
        is CaptureResult.Ok -> "photo OK"
        else -> "photo ${photo?.javaClass?.simpleName ?: "not attempted"}"
      }
      if (bytes.isEmpty()) return@withContext Result("HFP routed=$routed; no PCM; $photoText", "")
      wavBytes = wav(bytes)
      bytes.fill(0)
      val token = key()?.takeIf(String::isNotBlank)
          ?: return@withContext Result("HFP routed=$routed; PCM=${pcm.size()} bytes; $photoText; Muse key missing", "")
      val t0 = SystemClock.elapsedRealtime()
      val transcript = transcribe(token, wavBytes)
      val asrMs = SystemClock.elapsedRealtime() - t0
      wavBytes.fill(0)
      if (transcript.startsWith("ERROR:")) return@withContext Result("HFP routed=$routed; PCM=${pcm.size()} bytes; $photoText; ASR $transcript", "")
      val schema = JSONObject().put("type", "object")
          .put("properties", JSONObject().put("summary", JSONObject().put("type", "string"))
              .put("confident", JSONObject().put("type", "boolean")))
          .put("required", org.json.JSONArray().put("summary").put("confident"))
          .put("additionalProperties", false)
      val spark = muse.structured("conversation spike", "Summarize only the transcript in at most 12 words. If unclear, confident=false and summary empty.",
          listOf(muse.textPart(transcript)), "conversation_spike", schema)
      val sparkText = when (spark) {
        is MuseClient.Result.Ok -> "Spark ${spark.ms}ms, confident=${spark.json.optBoolean("confident")}, summary=${spark.json.optString("summary")}" 
        is MuseClient.Result.Failed -> "Spark failed ${spark.kind} ${spark.ms}ms"
      }
      Result("HFP routed=$routed; PCM=${pcm.size()} bytes (nonzero=$nonzero); $photoText; ASR ${asrMs}ms; $sparkText", transcript)
    } catch (e: Exception) {
      Result("Spike failed: ${e.javaClass.simpleName} (no audio saved)", "")
    } finally {
      recorder?.runCatching { stop() }
      recorder?.release()
      audio.clearCommunicationDevice()
      audio.mode = oldMode
      chunk.fill(0)
      wavBytes.fill(0)
      pcm.wipe()
    }
  }

  private fun wav(pcm: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(pcm.size + 44)
    fun word(v: Int) { repeat(4) { out.write((v ushr (it * 8)) and 255) } }
    fun half(v: Int) { out.write(v and 255); out.write((v ushr 8) and 255) }
    out.write("RIFF".toByteArray()); word(pcm.size + 36); out.write("WAVEfmt ".toByteArray())
    word(16); half(1); half(1); word(16_000); word(32_000); half(2); half(16)
    out.write("data".toByteArray()); word(pcm.size); out.write(pcm)
    return out.toByteArray()
  }

  private fun transcribe(token: String, wav: ByteArray): String {
    val boundary = "mystspike${SystemClock.elapsedRealtime()}"
    val conn = URL("https://api.meta.ai/v1/asr/transcribe").openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"; conn.connectTimeout = 15_000; conn.readTimeout = 60_000
      conn.doOutput = true
      conn.setRequestProperty("Authorization", "Bearer $token")
      conn.setRequestProperty("Accept", "application/json")
      conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
      conn.outputStream.use { out ->
        fun part(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        part("--$boundary\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n")
        part("{\"model\":\"muse-voice-transcribe-1.0\",\"audioEncoding\":\"WAV\",\"mode\":\"DIARIZATION\"}\r\n")
        part("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"clip.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
        out.write(wav); part("\r\n--$boundary--\r\n")
      }
      if (conn.responseCode !in 200..299) return "ERROR: HTTP ${conn.responseCode}"
      return JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("transcript")
    } finally { conn.disconnect() }
  }
}
