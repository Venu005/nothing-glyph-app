package app.backlit.alerts

/** Decides which alert (if any) is playing. Pure: callers pass the time. */
class AlertCoordinator(
    private val contacts: () -> List<ContactRule>,
    private val devices: () -> List<DeviceRule>,
) {
    var active: ActiveAlert? = null
        private set

    private val lastDeviceFire = mutableMapOf<String, Long>()

    private data class MissedReminder(val key: String, val animationId: String, val nextAt: Long, val remaining: Int)
    private var missed: MissedReminder? = null

    fun onCallRinging(name: String, nowMs: Long) {
        val rule = contacts().firstOrNull { NameMatch.matches(it.name, name) } ?: return
        active = ActiveAlert(rule.animationId, AlertKind.CALL, nowMs, nowMs + CALL_CAP_MS)
    }

    fun onCallEnded() {
        if (active?.kind == AlertKind.CALL) active = null
    }

    fun onDeviceConnected(address: String, nowMs: Long) {
        val rule = devices().firstOrNull { it.address.equals(address, ignoreCase = true) } ?: return
        if (active?.kind == AlertKind.CALL) return
        val key = rule.address.uppercase()
        val last = lastDeviceFire[key]
        if (last != null && nowMs - last < DEVICE_COOLDOWN_MS) return
        lastDeviceFire[key] = nowMs
        active = ActiveAlert(rule.animationId, AlertKind.DEVICE, nowMs, nowMs + SHORT_MS)
    }

    /** Missed call from an important contact: 10 s now, then a 5 s reminder every minute (max 10) until cleared. */
    fun onMissedCall(key: String, texts: List<String>, nowMs: Long) {
        val rule = contacts().firstOrNull { c -> texts.any { NameMatch.matches(c.name, it) || NameMatch.containsName(it, c.name) } } ?: return
        if (missed?.key == key && missed?.animationId == rule.animationId) return   // same notification updated: keep reminding
        missed = MissedReminder(key, rule.animationId, nowMs + MISSED_MS + REMINDER_EVERY_MS, MAX_REMINDERS)
        if (active?.kind == AlertKind.CALL) return
        active = ActiveAlert(rule.animationId, AlertKind.MISSED, nowMs, nowMs + MISSED_MS)
    }

    /** Only the notification that started the reminders can stop them. */
    fun onMissedCleared(key: String) {
        if (missed?.key != key) return
        missed = null
        if (active?.kind == AlertKind.MISSED) active = null
    }

    /** When the caller should next call [tick] (end of the active alert or the next reminder). */
    fun nextWakeAt(): Long? = active?.endsAt ?: missed?.nextAt

    fun preview(animationId: String, nowMs: Long, durationMs: Long = SHORT_MS) {
        if (active?.kind == AlertKind.CALL) return
        active = ActiveAlert(animationId, AlertKind.DEVICE, nowMs, nowMs + durationMs)
    }

    fun tick(nowMs: Long) {
        val a = active
        if (a != null && nowMs >= a.endsAt) active = null
        val m = missed ?: return
        if (active == null && nowMs >= m.nextAt) {
            active = ActiveAlert(m.animationId, AlertKind.MISSED, nowMs, nowMs + REMINDER_MS)
            missed = if (m.remaining > 1) m.copy(nextAt = nowMs + REMINDER_EVERY_MS, remaining = m.remaining - 1) else null
        }
    }

    companion object {
        const val CALL_CAP_MS = 60_000L
        const val SHORT_MS = 3_000L
        const val DEVICE_COOLDOWN_MS = 30_000L
        const val MISSED_MS = 10_000L
        const val REMINDER_MS = 5_000L
        const val REMINDER_EVERY_MS = 60_000L
        const val MAX_REMINDERS = 10
    }
}
