package app.backlit.alerts

/**
 * Turns the Phone app's call notifications into ringing / ended events.
 * Ringing = a CALL notification marked incoming. Ended = the same notification becomes non-incoming
 * (answered) or is removed (declined / missed / hung up).
 */
class CallNotificationTracker {
    sealed interface Event {
        data class Ringing(val callerName: String) : Event
        data object Ended : Event
    }

    private var ringingKey: String? = null

    fun onPosted(key: String, isCall: Boolean, incoming: Boolean, title: String?): Event? {
        if (!isCall) return null
        if (incoming) {
            if (key == ringingKey) return null
            val name = title?.takeIf { it.isNotBlank() } ?: return null
            ringingKey = key
            return Event.Ringing(name)
        }
        if (key == ringingKey) {
            ringingKey = null
            return Event.Ended
        }
        return null
    }

    fun onRemoved(key: String): Event? {
        if (key != ringingKey) return null
        ringingKey = null
        return Event.Ended
    }
}
