package com.myst.features.meds

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.myst.MainActivity
import com.myst.R
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.core.guard.ResponseGuard
import com.myst.core.records.Event
import com.myst.core.records.EventType
import com.myst.core.records.HouseholdStore
import com.myst.core.records.Medication
import com.myst.core.records.PeopleStore
import java.util.Locale
import java.util.UUID

/**
 * One daily reminder per saved time. Speech plays through normal media audio, which reaches the
 * glasses over Bluetooth when they are connected. This does not track whether a dose was taken.
 */
object DoseReminders {
  private const val TAG = "Myst:Doses"
  private const val EXTRA_MED = "med"
  private const val EXTRA_STAGE = "stage"
  const val CHANNEL_REMINDERS = "dose_reminders"

  val capability =
      Capability(
          "Medication reminders",
          Label.LIVE,
          "One daily Android alarm on this phone, spoken with the OS voice (not a Meta API)",
          blockedBy = "D5 reading: are OS alarms acceptable?",
      )

  fun canScheduleExact(context: Context): Boolean =
      context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

  /** Re-plan one daily alarm per reminder. Call after setup changes, at start and at boot. */
  fun rescheduleAll(context: Context) {
    val meds = HouseholdStore(context).medications()
    meds.forEach {
      cancel(context, it.id) // Also removes follow-up alarms left by an older build.
      schedule(context, it, DoseSchedule.nextReminderTime(it, System.currentTimeMillis()))
    }
  }

  fun cancel(context: Context, medId: String) {
    val am = context.getSystemService(AlarmManager::class.java)
    DoseStage.entries.forEach { am.cancel(pending(context, medId, it)) }
  }

  private fun schedule(context: Context, med: Medication, doseTime: Long) {
    val am = context.getSystemService(AlarmManager::class.java)
    val now = System.currentTimeMillis()
    if (doseTime <= now) return
    val pi = pending(context, med.id, DoseStage.REMIND)
    if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, doseTime, pi)
    else am.setWindow(AlarmManager.RTC_WAKEUP, doseTime, 60_000L, pi)
    Log.i(TAG, "scheduled ${med.id.take(8)} for ${DoseSchedule.formatTimeOfDay(doseTime)}")
  }

  private fun pending(context: Context, medId: String, stage: DoseStage): PendingIntent =
      PendingIntent.getBroadcast(
          context,
          (medId + stage.name).hashCode(),
          Intent(context, DoseAlarmReceiver::class.java)
              .putExtra(EXTRA_MED, medId)
              .putExtra(EXTRA_STAGE, stage.name),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

  /** What happens at the reminder time. Old follow-up alarms are ignored. */
  internal fun fire(context: Context, med: Medication, stage: DoseStage, onDone: () -> Unit = {}) {
    if (stage != DoseStage.REMIND) return onDone()
    val store = HouseholdStore(context)
    val name = med.labelName
    val lines = DoseSchedule.spokenLines(med, stage)
    store.logReminder(
        Event(
            UUID.randomUUID().toString(),
            EventType.DOSE_REMINDED,
            System.currentTimeMillis(),
            medicationId = med.id,
        ),
    )
    notify(context, CHANNEL_REMINDERS, "Time for $name", lines.joinToString(" "), med.id.hashCode())
    scheduleNextDay(context, med)
    val people = PeopleStore(context).all()
    val guarded = lines.mapNotNull { (ResponseGuard.check(it, null, people) as? ResponseGuard.Decision.Pass)?.line?.text }
    speak(context, guarded, onDone)
  }

  private fun scheduleNextDay(context: Context, med: Medication) {
    HouseholdStore(context).medications().firstOrNull { it.id == med.id }?.let { real ->
      schedule(context, real, DoseSchedule.nextReminderTime(real, System.currentTimeMillis()))
    }
  }

  private fun notify(context: Context, channel: String, title: String, text: String, id: Int) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Dose reminders", NotificationManager.IMPORTANCE_HIGH))
    val open =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    runCatching {
          nm.notify(
              id,
              NotificationCompat.Builder(context, channel)
                  .setSmallIcon(R.drawable.ic_notification)
                  .setContentTitle(title)
                  .setContentText(text)
                  .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                  .setContentIntent(open)
                  .setAutoCancel(true)
                  .build(),
          )
        }
        .onFailure { Log.w(TAG, "notification not shown: ${it.message}") }
  }

  /** One-shot TTS for a receiver: slow, one voice, lines in order, then shut down. */
  private fun speak(context: Context, lines: List<String>, onDone: () -> Unit) {
    if (lines.isEmpty()) return onDone()
    var tts: TextToSpeech? = null
    tts =
        TextToSpeech(context.applicationContext) { status ->
          val engine = tts ?: return@TextToSpeech onDone()
          if (status != TextToSpeech.SUCCESS) {
            engine.shutdown()
            return@TextToSpeech onDone()
          }
          engine.language = Locale.US
          engine.setSpeechRate(0.85f)
          engine.setOnUtteranceProgressListener(
              object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                  if (id == "last") finish()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) = finish()

                private fun finish() {
                  engine.shutdown()
                  onDone()
                }
              },
          )
          lines.forEachIndexed { i, line ->
            engine.speak(line, TextToSpeech.QUEUE_ADD, null, if (i == lines.lastIndex) "last" else "l$i")
          }
        }
  }

  internal fun parse(intent: Intent): Pair<String, DoseStage>? {
    val med = intent.getStringExtra(EXTRA_MED) ?: return null
    val stage = intent.getStringExtra(EXTRA_STAGE)?.let { runCatching { DoseStage.valueOf(it) }.getOrNull() } ?: return null
    return med to stage
  }
}

class DoseAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val (medId, stage) = DoseReminders.parse(intent) ?: return
    val med = HouseholdStore(context).medications().firstOrNull { it.id == medId } ?: return
    val result = goAsync()
    DoseReminders.fire(context, med, stage) { result.finish() }
  }
}

/** Alarms do not survive a reboot; plan them again. */
class DoseBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) DoseReminders.rescheduleAll(context)
  }
}
