package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.badge.BadgeArt
import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/** A status sign: the current message's icon with its text scrolling underneath; long press switches messages. */
class BadgeToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null

    private var listJson: String? = null
    private var messages: List<BadgeMessage> = BadgeMessage.STARTERS
    private var shownSince: Long? = null
    private var tickerFrom = 0L
    private var flashFrom = Long.MIN_VALUE / 2
    private var loaded = false
    private var boundAt = 0L

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> next()
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(System.currentTimeMillis()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "badge toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        boundAt = System.currentTimeMillis()
        output = GlyphOutput(this, profile) { kick() }.also { it.connect() }
        s.launch {
            repo.update {
                var u = it
                if (!u.badgeToyEverBound) u = u.copy(badgeToyEverBound = true)
                if (u.badgeActiveSince == 0L) u = u.copy(badgeActiveSince = System.currentTimeMillis())
                u
            }
        }
        s.launch { repo.settings.collect { settings = it; loaded = true; kick() } }
        s.launch { rt.bus.collect { kick() } }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        renderJob?.cancel()
        renderJob = null
        scope?.cancel()
        scope = null
        output?.close()
        output = null
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
        return false
    }

    /** Long press: the next message becomes current, and its countdown / until starts now. */
    private fun next() {
        val s = scope ?: return
        s.launch {
            repo.update { cur ->
                val list = BadgeMessage.decodeList(cur.badgeMessages)
                val i = BadgeMessage.activeIndex(cur.badgeActive, list.size)
                cur.copy(badgeActive = (i + 1) % list.size, badgeActiveSince = System.currentTimeMillis())
            }
        }
    }

    private fun isAod() = modes.mode(System.currentTimeMillis()) == Mode.AOD

    private fun current(): Pair<Int, BadgeMessage> {
        if (settings.badgeMessages != listJson) {
            listJson = settings.badgeMessages
            messages = BadgeMessage.decodeList(settings.badgeMessages)
        }
        val i = BadgeMessage.activeIndex(settings.badgeActive, messages.size)
        return i to messages[i]
    }

    private fun kick() {
        val s = scope ?: return
        if (!loaded || renderJob?.isActive == true) return   // nothing until the real settings are in
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val now = AlertsRuntime.now()
                val wall = System.currentTimeMillis()
                val alert = alerts?.bus?.value
                val aod = isAod()
                val (_, msg) = current()
                val since = settings.badgeActiveSince.takeIf { it > 0 } ?: boundAt
                // A message becomes current only when `since` moves (long press, pick, edit, delete); reordering
                // or deleting another message changes the index but must not flash.
                if (since != shownSince) {
                    if (shownSince != null) flashFrom = now   // a change while showing flashes; the first frame doesn't
                    shownSince = since
                    tickerFrom = now
                }
                val zone = ZoneId.systemDefault()
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, now - alert.startedAt)
                        aod -> BadgeArt.still(profile.size, msg, BadgeText.short(msg, since, wall, settings.use24h, zone))
                        else -> BadgeArt.frame(profile.size, msg, BadgeText.scroll(msg, since, wall, settings.use24h, zone), now - tickerFrom, now - flashFrom, BadgeText.spanText(msg, settings.use24h))
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                if (aod && alert == null) {
                    modes.msUntilActive(System.currentTimeMillis())?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private companion object {
        const val TAG = "BacklitBadge"
        const val FRAME_MS = 50L
    }
}
