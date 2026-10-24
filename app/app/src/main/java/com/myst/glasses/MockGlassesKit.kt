package com.myst.glasses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import java.io.File

/**
 * MOCK. Pairs a simulated Ray-Ban Meta through DAT's Mock Device Kit so the full capture path runs
 * with no hardware. The "photo" is a generated placeholder card, never a real face.
 */
class MockGlassesKit(private val context: Context) {
  private var glasses: MockGlasses? = null

  val isEnabled: Boolean
    get() = MockDeviceKit.getInstance(context).isEnabled

  /** Returns null on success, or a reason. */
  fun enable(): String? {
    val kit = MockDeviceKit.getInstance(context)
    if (!kit.isEnabled) kit.enable()
    if (glasses != null) return null
    return kit.pairGlasses(GlassesModel.RAYBAN_META)
        .fold(
            { device ->
              device.powerOn()
              device.unfold()
              device.don()
              // The kit will not start a stream without a video feed. The feed is a plant clip from
              // Meta's sample (debug assets); the captured still is our own placeholder card.
              val feed = feedVideo() ?: return@fold "Mock feed missing (debug builds only)"
              device.services.camera.setCameraFeed(feed)
              device.services.camera.setCapturedImage(placeholderImage())
              glasses = device
              null
            },
            { error, _ -> "Mock pairing failed: $error" },
        )
  }

  /** Returns null on success, or a reason. Works only while the app is listening. */
  fun simulateSpeech(text: String): String? {
    val speech = glasses?.services?.speech ?: return "Turn on simulated glasses first."
    if (!speech.isListening) return "Turn on Listening first."
    speech.simulateTranscription(text, true, 0.9f)
    return null
  }

  fun disable() {
    MockDeviceKit.getInstance(context).disable()
    glasses = null
  }

  private fun feedVideo(): Uri? {
    val file = File(context.cacheDir, "mock_feed.mp4")
    if (!file.exists()) {
      runCatching {
            context.assets.open("mock_feed.mp4").use { input ->
              file.outputStream().use { input.copyTo(it) }
            }
          }
          .onFailure { return null }
    }
    return Uri.fromFile(file)
  }

  private fun placeholderImage(): Uri {
    val file = File(context.cacheDir, "mock_capture.png")
    if (!file.exists()) {
      val bmp = Bitmap.createBitmap(504, 896, Bitmap.Config.ARGB_8888)
      Canvas(bmp).apply {
        drawColor(Color.rgb(60, 60, 72))
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
              color = Color.WHITE
              textSize = 44f
              textAlign = Paint.Align.CENTER
            }
        drawText("MOCK PHOTO", 252f, 420f, paint)
        paint.textSize = 26f
        drawText("DAT Mock Device Kit", 252f, 470f, paint)
        drawText("not a real person", 252f, 505f, paint)
      }
      file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return Uri.fromFile(file)
  }
}
