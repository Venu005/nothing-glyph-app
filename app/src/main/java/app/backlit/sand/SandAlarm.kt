package app.backlit.sand

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.SettingsRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Schedules the hourglass's done moment so it fires even when the toy isn't on screen. */
object SandAlarm {
    const val ACTION_DONE = "app.backlit.sand.DONE"
    private const val TAG = "BacklitSand"
    /** How long the chime may play before it is stopped and released. */
    const val CHIME_MS = 4_000L
    private val PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)

    /** True while the toy's ACTIVE render loop runs: it then handles "done" itself. */
    @Volatile var liveLoop = false

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, SandAlarmReceiver::class.java).setAction(ACTION_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancel(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(pending(context)) }
    }

    fun sync(context: Context, st: TimerState, exact: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        am.cancel(pi)
        if (st.phase != Phase.RUNNING) return
        runCatching {
            if (exact && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, st.endAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, st.endAt, pi)
        }.onFailure { Log.w(TAG, "schedule failed; falling back to inexact", it); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, st.endAt, pi) }
    }

    /** "glyph": nothing extra; "vibrate": the pattern; "chime": the pattern plus the notification sound when the ringer is on. */
    fun ring(context: Context, alert: String) {
        if (alert == "glyph") return
        runCatching {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                // Alarm usage: an unattributed vibration from a background receiver is dropped by Android.
                ?.vibrate(VibrationEffect.createWaveform(PATTERN, -1), VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        }.onFailure { Log.w(TAG, "vibrate failed", it) }
        if (alert != "chime") return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        // Played as an alarm sound and released after CHIME_MS. With the Ringtone defaults it counts as a ringtone,
        // and Nothing OS keeps flashing its ringtone Glyph effect until the player is released.
        runCatching {
            val r = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.isLooping = false
            r.play()
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { r.stop() } }, CHIME_MS)
        }.onFailure { Log.w(TAG, "chime failed", it) }
    }

    /** Alarm (or boot) path: finish a due RUNNING timer, ring, and play "Flip me" on the matrix if no toy is showing. */
    suspend fun finishFromAlarm(context: Context, rescheduleIfNotDue: Boolean) {
        val app = context.applicationContext
        val repo = SettingsRepo.get(app)
        val s = repo.settings.first()
        val st = TimerState.decode(s.sandTimer)
        val done = st.alarmFired(System.currentTimeMillis())
        if (done == null) {
            if (rescheduleIfNotDue) sync(app, st, s.sandExact)
            return
        }
        if (liveLoop) return
        repo.update { it.copy(sandTimer = done.encode()) }
        ring(app, s.sandAlert)
        AlertsRuntime.get(app).preview(SandPreviewAnimation.DONE_ID, TimerState.FLIP_ME_MS + 700)
        // Keep the receiver (and so the process) alive until the chime is stopped and released.
        if (s.sandAlert == "chime") delay(CHIME_MS + 200)
    }
}
