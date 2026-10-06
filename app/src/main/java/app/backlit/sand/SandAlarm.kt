package app.backlit.sand

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.SettingsRepo
import kotlinx.coroutines.flow.first

/** Schedules the hourglass's done moment so it fires even when the toy isn't on screen. */
object SandAlarm {
    const val ACTION_DONE = "app.backlit.sand.DONE"
    private const val TAG = "BacklitSand"
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
                ?.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
        }.onFailure { Log.w(TAG, "vibrate failed", it) }
        if (alert != "chime") return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching { RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
            .onFailure { Log.w(TAG, "chime failed", it) }
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
    }
}
