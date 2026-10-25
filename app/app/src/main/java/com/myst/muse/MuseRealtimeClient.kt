package com.myst.muse

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import org.json.JSONObject

/** Minimal RFC 6455 client for Meta Voice Transcribe's realtime endpoint; no third-party SDK. */
internal class MuseRealtimeClient private constructor(
    private val socket: SSLSocket,
    private val input: InputStream,
    private val output: OutputStream,
) : AutoCloseable {
  private val writeLock = Any()

  data class Event(val type: String, val json: JSONObject)

  fun sendAudio(pcm: ByteArray, length: Int) = sendFrame(0x2, pcm, length)

  fun endInput() = sendText(JSONObject().put("type", "endStream").toString())

  fun readEvents(onEvent: (Event) -> Unit) {
    while (true) {
      val message = readMessage() ?: return
      when (message.first) {
        0x8 -> return
        0x9 -> synchronized(writeLock) { writeFrame(0xA, message.second, message.second.size) }
        0x1 -> {
          val event = JSONObject(String(message.second, Charsets.UTF_8))
          val type = event.optString("type")
          if (type == "error") throw IllegalStateException("transcription service error")
          if (type.isNotEmpty()) onEvent(Event(type, event))
        }
      }
    }
  }

  private fun sendText(text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    try { sendFrame(0x1, bytes, bytes.size) } finally { bytes.fill(0) }
  }

  private fun sendFrame(opcode: Int, bytes: ByteArray, length: Int) {
    synchronized(writeLock) { writeFrame(opcode, bytes, length) }
  }

  /** Client frames must be masked. The audio frame is small (80 ms at 16 kHz mono). */
  private fun writeFrame(opcode: Int, bytes: ByteArray, length: Int) {
    require(length <= bytes.size)
    val mask = ByteArray(4).also(SecureRandom()::nextBytes)
    output.write(0x80 or opcode)
    when {
      length < 126 -> output.write(0x80 or length)
      length <= 0xffff -> {
        output.write(0x80 or 126)
        output.write(length ushr 8)
        output.write(length)
      }
      else -> {
        output.write(0x80 or 127)
        repeat(4) { output.write(0) }
        for (shift in 24 downTo 0 step 8) output.write(length ushr shift)
      }
    }
    output.write(mask)
    val masked = ByteArray(length)
    for (i in 0 until length) masked[i] = (bytes[i].toInt() xor mask[i and 3].toInt()).toByte()
    output.write(masked)
    output.flush()
    masked.fill(0)
    mask.fill(0)
  }

  private fun readMessage(): Pair<Int, ByteArray>? {
    var opcode = -1
    val message = ByteArrayOutputStream()
    while (true) {
      val first = input.read()
      if (first < 0) return null
      val second = input.read()
      if (second < 0) return null
      val currentOpcode = first and 0x0f
      var length = (second and 0x7f).toLong()
      if (length == 126L) length = ((input.read().toLong() and 255) shl 8) or (input.read().toLong() and 255)
      else if (length == 127L) {
        length = 0
        repeat(8) { length = (length shl 8) or (input.read().toLong() and 255) }
      }
      require(length <= MAX_SERVER_FRAME_BYTES) { "oversized transcription response" }
      val masked = second and 0x80 != 0
      val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
      val payload = ByteArray(length.toInt()).also { input.readFully(it) }
      if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
      if (currentOpcode == 0x9 || currentOpcode == 0xA || currentOpcode == 0x8) return currentOpcode to payload
      if (currentOpcode != 0x0) opcode = currentOpcode
      message.write(payload)
      if (first and 0x80 != 0) return opcode to message.toByteArray()
    }
  }

  override fun close() { runCatching { socket.close() } }

  private fun sendHandshake(token: String) {
    val key = ByteArray(16).also(SecureRandom()::nextBytes).let(Base64.getEncoder()::encodeToString)
    val id = UUID.randomUUID().toString()
    val request = buildString {
      append("GET /v1/asr/realtime?sessionId=").append(id).append(" HTTP/1.1\r\n")
      append("Host: api.meta.ai\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n")
      append("Sec-WebSocket-Key: ").append(key).append("\r\nSec-WebSocket-Version: 13\r\n\r\n")
    }
    output.write(request.toByteArray(Charsets.US_ASCII))
    output.flush()
    val headers = readHttpHeaders(input)
    check(headers.startsWith("HTTP/1.1 101")) { "transcription connection rejected" }
    val expected = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray(Charsets.US_ASCII)),
    )
    check(headers.lineSequence().any { it.equals("Sec-WebSocket-Accept: $expected", ignoreCase = true) }) {
      "invalid transcription handshake"
    }
    sendText(
        JSONObject()
            .put("authorization", JSONObject().put("accessToken", "Bearer $token"))
            .put("audioEncoding", "PCM_16KHZ")
            .put("model", "muse-voice-transcribe-1.0")
            .put("mode", "ENDPOINTING")
            .put("partialMode", "CUMULATIVE")
            .put("emitAudioProgress", true)
            .toString(),
    )
    val ack = readMessage()
    check(ack?.first == 0x1 && JSONObject(String(ack.second, Charsets.UTF_8)).has("sessionId")) {
      "transcription handshake was not acknowledged"
    }
  }

  companion object {
    private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val MAX_SERVER_FRAME_BYTES = 1_048_576L

    fun connect(token: String): MuseRealtimeClient {
      val socket = (SSLSocketFactory.getDefault().createSocket() as SSLSocket).apply {
        soTimeout = 75_000
        connect(InetSocketAddress("api.meta.ai", 443), 15_000)
        startHandshake()
      }
      val client = MuseRealtimeClient(socket, socket.inputStream, socket.outputStream)
      try {
        client.sendHandshake(token)
        return client
      } catch (e: Exception) {
        client.close()
        throw e
      }
    }

    private fun readHttpHeaders(input: InputStream): String {
      val bytes = ByteArrayOutputStream()
      var matched = 0
      while (bytes.size() < 16_384) {
        val b = input.read()
        if (b < 0) break
        bytes.write(b)
        matched = when {
          matched == 0 && b == 13 -> 1
          matched == 1 && b == 10 -> 2
          matched == 2 && b == 13 -> 3
          matched == 3 && b == 10 -> 4
          b == 13 -> 1
          else -> 0
        }
        if (matched == 4) return bytes.toString(Charsets.US_ASCII.name())
      }
      throw IllegalStateException("transcription handshake headers incomplete")
    }

    private fun InputStream.readFully(bytes: ByteArray) {
      var offset = 0
      while (offset < bytes.size) {
        val count = read(bytes, offset, bytes.size - offset)
        if (count < 0) throw java.io.EOFException()
        offset += count
      }
    }
  }
}
