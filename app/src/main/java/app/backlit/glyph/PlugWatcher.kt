package app.backlit.glyph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

/**
 * Remembers when the charger was last connected, so the Charge toy can replay its plug-in animation when
 * Nothing hands the matrix back after its own charge animation. Passive: registered once per process, only
 * while the process is already running (Charge toy or the Alerts listener); it never starts or wakes anything.
 */
object PlugWatcher {
    @Volatile var lastPluggedAt: Long? = null
        private set
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> lastPluggedAt = SystemClock.elapsedRealtime()
                Intent.ACTION_POWER_DISCONNECTED -> lastPluggedAt = null
            }
        }
    }

    @Synchronized
    fun ensure(context: Context) {
        if (registered) return
        runCatching {
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                },
                Context.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
    }
}
