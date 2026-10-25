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
import com.myst.core.guard.ResponseGuard
import com.myst.core.records.Event
import com.myst.core.records.EventType
import com.myst.core.records.HouseholdStore
import com.myst.core.records.Medication
import com.myst.core.records.PeopleStore
import java.util.Locale
import java.util.UUID

/**
 * Dose reminders on Android AlarmManager (host OS, like TTS: see Q11/D5). Each medication gets its
 * stages for the current dose; each alarm re-checks "reported taken" when it fires, and the last
 * stage schedules the next day. Speech plays through normal media audio, which reaches the glasses
 * over Bluetooth when they are connected.
 */
object DoseReminders {
  private const val TAG = "Myst:Doses"
  private const val EXTRA_MED = "med"
  private const val EXTRA_STAGE = "stage"
  private const val EXTRA_DOSE_TIME = "doseTime"
  private const val PREFS = "dose_reminders"
  private const val KEY_FAST = "fastDemo"
  const val CHANNEL_REMINDERS = "dose_reminders"
  const val CHANNEL_ALERTS = "caregiver_alerts"

  fun fastDemo(context: Context): Boolean =
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FAST, false)

  fun setFastDemo(context: Context, on: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FAST, on).apply()
  }

  private fun timing(context: Context) = if (fastDemo(context)) DoseSchedule.FAST_DEMO else DoseSchedule.NORMAL

  fun canScheduleExact(context: Context): Boolean =
      context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

  /** Re-plan every medication's upcoming stages. Call after setup changes, at start and at boot. */
  fun rescheduleAll(context: Context) {
    val meds = HouseholdStore(context).medications()
    meds.forEach { schedule(context, it, DoseSchedule.currentDoseTime(it, System.currentTimeMillis(), timing(context))) }
  }

  fun cancel(context: Context, medId: String) {
    val am = context.getSystemService(AlarmManager::class.java)
    DoseStage.entries.forEach { am.cancel(pending(context, medId, it, 0L)) }
  }

  /** Demo control (R15): run the reminder now, with follow-ups timed from now. */
  fun remindNow(context: Context, med: Medication) {
    val now = System.currentTimeMillis()
    cancel(context, med.id)
    schedule(context, med, now, skipFirst = true)
    fire(context, med, DoseStage.REMIND, now)
  }

  private fun schedule(context: Context, med: Medication, doseTime: Long, skipFirst: Boolean = false) {
    val am = context.getSystemService(AlarmManager::class.java)
    val now = System.currentTimeMillis()
    DoseSchedule.offsets(med, timing(context)).forEach { (stage, offsetMin) ->
      if (skipFirst && stage == DoseStage.REMIND) return@forEach
      val at = doseTime + offsetMin * 60_000L
      if (at <= now) return@forEach
      val pi = pending(context, med.id, stage, doseTime)
      if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
      else am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi)
    }
    Log.i(TAG, "scheduled ${med.id.take(8)} for ${DoseSchedule.formatTimeOfDay(doseTime)}")
  }

  private fun pending(context: Context, medId: String, stage: DoseStage, doseTime: Long): PendingIntent =
      PendingIntent.getBroadcast(
          context,
          (medId + stage.name).hashCode(),
          Intent(context, DoseAlarmReceiver::class.java)
              .putExtra(EXTRA_MED, medId)
              .putExtra(EXTRA_STAGE, stage.name)
              .putExtra(EXTRA_DOSE_TIME, doseTime),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

  /** What happens when a stage fires. Returns after posting; speech finishes asynchronously. */
  internal fun fire(context: Context, med: Medication, stage: DoseStage, doseTime: Long, onDone: () -> Unit = {}) {
    val store = HouseholdStore(context)
    val reported = DoseSchedule.reportedTaken(med, doseTime, store.events())
    if (reported != null) {
      Log.i(TAG, "${stage.name}: already reported taken, nothing to do")
      if (stage == DoseStage.WINDOW_CLOSED) scheduleNextDay(context, med, doseTime)
      onDone()
      return
    }
    val places = store.places()
    val kept = places.firstOrNull { it.id == med.storedInPlaceId }?.name?.lowercase()
    val food = places.firstOrNull { it.id == med.foodPlaceId }?.name?.lowercase()
    // "in the bathroom, in the cabinet above the sink"
    val spot =
        med.storageNote.trim().takeIf(String::isNotEmpty)?.let { note ->
          if (Regex("^(in|on|under|next to|beside|behind|by)\\b", RegexOption.IGNORE_CASE).containsMatchIn(note)) note
          else "in the $note"
        }
    val where = listOfNotNull(kept?.let { "in the $it" }, spot).joinToString(", ")
    val name = med.labelName
    val lines =
        when (stage) {
          DoseStage.REMIND ->
              listOfNotNull(
                  "It's time for your ${DoseSchedule.partOfDay(med.doseMinuteOfDay)} $name." +
                      (if (where.isNotBlank()) " It's $where." else ""),
                  if (med.withFood) "This one goes with food, so let's go to the ${food ?: "kitchen"} first." else null,
              )
          DoseStage.REREMIND_1, DoseStage.REREMIND_2 ->
              listOf("Just a reminder, your $name is ready for you" + (if (where.isNotBlank()) ", $where." else "."))
          DoseStage.WINDOW_CLOSED -> emptyList()
        }

    if (stage == DoseStage.WINDOW_CLOSED) {
      val time = DoseSchedule.formatTime(med.doseMinuteOfDay)
      store.log(Event(UUID.randomUUID().toString(), EventType.DOSE_MISSED, System.currentTimeMillis(), medicationId = med.id, detail = "not reported taken by window close ($time)"))
      notify(context, CHANNEL_ALERTS, "Missed dose: $name", "Scheduled $time; not reported taken. (Alert on this phone; WhatsApp is a mock, Q13.)", med.id.hashCode() + 1)
      scheduleNextDay(context, med, doseTime)
      onDone()
      return
    }

    notify(context, CHANNEL_REMINDERS, "Time for $name", lines.joinToString(" "), med.id.hashCode())
    val people = PeopleStore(context).all()
    val guarded = lines.mapNotNull { (ResponseGuard.check(it, null, people) as? ResponseGuard.Decision.Pass)?.line?.text }
    speak(context, guarded, onDone)
  }

  private fun scheduleNextDay(context: Context, med: Medication, doseTime: Long) {
    // Always plan from the real dose time, even after a "remind now" demo.
    HouseholdStore(context).medications().firstOrNull { it.id == med.id }?.let { real ->
      schedule(context, real, DoseSchedule.doseTimeOn(real, doseTime + 24 * 60 * 60_000L))
    }
  }

  private fun notify(context: Context, channel: String, title: String, text: String, id: Int) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Dose reminders", NotificationManager.IMPORTANCE_HIGH))
    nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Caregiver alerts", NotificationManager.IMPORTANCE_HIGH))
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

  internal fun parse(intent: Intent): Triple<String, DoseStage, Long>? {
    val med = intent.getStringExtra(EXTRA_MED) ?: return null
    val stage = intent.getStringExtra(EXTRA_STAGE)?.let { runCatching { DoseStage.valueOf(it) }.getOrNull() } ?: return null
    return Triple(med, stage, intent.getLongExtra(EXTRA_DOSE_TIME, 0L))
  }
}

class DoseAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val (medId, stage, doseTime) = DoseReminders.parse(intent) ?: return
    val med = HouseholdStore(context).medications().firstOrNull { it.id == medId } ?: return
    val result = goAsync()
    DoseReminders.fire(context, med, stage, doseTime) { result.finish() }
  }
}

/** Alarms do not survive a reboot; plan them again. */
class DoseBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) DoseReminders.rescheduleAll(context)
  }
}
