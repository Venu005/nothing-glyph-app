package app.backlit.ui.alerts

enum class AlertsSegment(val label: String) { CONTACTS("CONTACTS"), DEVICES("DEVICES"), ANIMATIONS("ANIMATIONS") }

enum class Attention { NOTIFICATION_ACCESS, NEARBY }

/** At most one "needs attention" card on Alerts, most important first. */
object AlertsAttention {
    fun pick(listenerOn: Boolean, btGranted: Boolean, contacts: Int, devices: Int, segment: AlertsSegment): Attention? = when {
        !listenerOn && (contacts > 0 || segment == AlertsSegment.CONTACTS) -> Attention.NOTIFICATION_ACCESS
        !btGranted && devices > 0 -> Attention.NEARBY
        else -> null
    }
}
