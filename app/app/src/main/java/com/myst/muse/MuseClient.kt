package com.myst.muse

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Model IDs and limits in one place. Standard mode only: never a `-contributor` model. */
object MuseConfig {
  const val BASE_URL = "https://api.meta.ai/v1"
  const val MODEL = "muse-spark-1.3"
  const val TIMEOUT_MS = 15_000
  const val MAX_TOKENS = 300

  init {
    require(!MODEL.endsWith("-contributor")) { "Contributor models train on our data" }
  }
}

/**
 * Muse Spark over plain HTTPS (D5), Chat Completions format. Per kchperla's device run,
 * reasoning_effort "minimal" is required or the answer often comes back empty.
 * Logs task, latency and outcome only: never images, transcripts or the key.
 *
 * [apiKey] comes from MuseKeyStore (Android Keystore), not BuildConfig; it is read on the IO
 * thread for each call, and null means no key was provisioned.
 */
class MuseClient(private val apiKey: () -> String?) {
  companion object {
    private const val TAG = "Myst:Muse"
  }

  /** Why a call gave no verdict. Callers say "I can't check right now", never "not sure". */
  enum class FailKind {
    NO_KEY,
    AUTH,
    TIMEOUT,
    NETWORK,
    HTTP,
    /** Empty, refused, cut off or unparseable answer. */
    BAD_ANSWER,
  }

  sealed interface Result {
    data class Ok(val json: JSONObject, val ms: Long) : Result
    data class Failed(val reason: String, val ms: Long, val kind: FailKind = FailKind.BAD_ANSWER) : Result
  }

  /** A JPEG as a user-message image part. */
  fun imagePart(jpeg: ByteArray): JSONObject =
      JSONObject()
          .put("type", "image_url")
          .put(
              "image_url",
              JSONObject().put("url", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)),
          )

  fun textPart(text: String): JSONObject = JSONObject().put("type", "text").put("text", text)

  /** One structured-output call. A parse failure is a failure, which callers treat as unsure. */
  suspend fun structured(
      task: String,
      system: String,
      userParts: List<JSONObject>,
      schemaName: String,
      schema: JSONObject,
      reasoningEffort: String = "minimal",
      maxTokens: Int = MuseConfig.MAX_TOKENS,
      model: String = MuseConfig.MODEL,
  ): Result =
      withContext(Dispatchers.IO) {
        val t0 = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - t0
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.Failed("no Muse key on this phone", 0, FailKind.NO_KEY)
        val body =
            JSONObject()
                .put("model", model.also { require(!it.endsWith("-contributor")) })
                .put("reasoning_effort", reasoningEffort)
                .put("max_tokens", maxTokens)
                .put(
                    "response_format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put(
                            "json_schema",
                            JSONObject().put("name", schemaName).put("strict", true).put("schema", schema),
                        ),
                )
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", JSONArray(userParts))),
                )
        val result =
            try {
              val conn = URL("${MuseConfig.BASE_URL}/chat/completions").openConnection() as HttpURLConnection
              conn.requestMethod = "POST"
              conn.connectTimeout = MuseConfig.TIMEOUT_MS
              conn.readTimeout = MuseConfig.TIMEOUT_MS
              conn.doOutput = true
              conn.setRequestProperty("Authorization", "Bearer $key")
              conn.setRequestProperty("Content-Type", "application/json")
              conn.outputStream.use { it.write(body.toString().toByteArray()) }
              val code = conn.responseCode
              if (code == 401 || code == 403) {
                Result.Failed("HTTP $code", ms(), FailKind.AUTH)
              } else if (code !in 200..299) {
                Result.Failed("HTTP $code", ms(), FailKind.HTTP)
              } else {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val choice = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                val content =
                    choice.getJSONObject("message").optString("content").trim()
                        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val finish = choice.optString("finish_reason")
                when {
                  content.isBlank() -> Result.Failed("empty answer (finish=$finish)", ms())
                  // Model text can echo a personal conversation. Log only the outcome category.
                  !content.startsWith("{") ->
                      Result.Failed("not JSON (finish=$finish)", ms())
                  else -> Result.Ok(JSONObject(content), ms())
                }
              }
            } catch (e: CancellationException) {
              throw e
            } catch (e: SocketTimeoutException) {
              Result.Failed("timeout", ms(), FailKind.TIMEOUT)
            } catch (e: IOException) {
              Result.Failed(e.javaClass.simpleName, ms(), FailKind.NETWORK)
            } catch (e: Exception) {
              // JSONException and friends: the answer came back but was not usable.
              Result.Failed(e.javaClass.simpleName, ms(), FailKind.BAD_ANSWER)
            }
        when (result) {
          is Result.Ok -> Log.i(TAG, "$task ok ${result.ms}ms")
          is Result.Failed -> Log.w(TAG, "$task failed ${result.ms}ms: ${result.reason}")
        }
        result
      }
}
