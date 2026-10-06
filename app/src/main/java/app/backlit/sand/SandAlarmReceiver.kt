package app.backlit.sand

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The done alarm, plus re-arming after a reboot or an app update. */
class SandAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                SandAlarm.finishFromAlarm(context, rescheduleIfNotDue = intent.action != SandAlarm.ACTION_DONE)
            } catch (e: Exception) {
                Log.e("BacklitSand", "alarm handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
