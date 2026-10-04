package app.backlit.alerts

/** Decides which alert (if any) is playing. Pure: callers pass the time. */
class AlertCoordinator(
    private val contacts: () -> List<ContactRule>,
    private val devices: () -> List<DeviceRule>,
) {
    var active: ActiveAlert? = null
        private set

    private val lastDeviceFire = mutableMapOf<String, Long>()

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

    fun preview(animationId: String, nowMs: Long) {
        if (active?.kind == AlertKind.CALL) return
        active = ActiveAlert(animationId, AlertKind.DEVICE, nowMs, nowMs + SHORT_MS)
    }

    fun tick(nowMs: Long) {
        val a = active ?: return
        if (nowMs >= a.endsAt) active = null
    }

    companion object {
        const val CALL_CAP_MS = 60_000L
        const val SHORT_MS = 3_000L
        const val DEVICE_COOLDOWN_MS = 30_000L
    }
}
