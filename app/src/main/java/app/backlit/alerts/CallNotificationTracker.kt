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
        data class Missed(val key: String, val texts: List<String>) : Event
        data class MissedCleared(val key: String) : Event
    }

    private var ringingKey: String? = null
    private val missed = mutableMapOf<String, List<String>>()   // key → last texts seen

    fun onPosted(
        key: String, isCall: Boolean, incoming: Boolean, title: String?,
        isMissedCall: Boolean = false, text: String? = null,
    ): Event? {
        if (isMissedCall) {
            val texts = listOfNotNull(title, text).filter { it.isNotBlank() }
            if (texts.isEmpty() || missed[key] == texts) return null
            missed[key] = texts
            return Event.Missed(key, texts)
        }
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
        if (missed.remove(key) != null) return Event.MissedCleared(key)
        if (key != ringingKey) return null
        ringingKey = null
        return Event.Ended
    }
}
