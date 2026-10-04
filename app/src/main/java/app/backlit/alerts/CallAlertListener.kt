package app.backlit.alerts

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** Watches only incoming-call notifications; everything else is ignored immediately. Never logs contents. */
class CallAlertListener : NotificationListenerService() {
    private val tracker = CallNotificationTracker()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val isCall = n.category == Notification.CATEGORY_CALL
        val isMissed = n.category == Notification.CATEGORY_MISSED_CALL
        if (!isCall && !isMissed) return
        val incoming = n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0) == Notification.CallStyle.CALL_TYPE_INCOMING
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        handle(tracker.onPosted(sbn.key, isCall = isCall, incoming = incoming, title = title, isMissedCall = isMissed, text = text))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = handle(tracker.onRemoved(sbn.key))

    private fun handle(event: CallNotificationTracker.Event?) {
        val rt = AlertsRuntime.get(this)
        when (event) {
            is CallNotificationTracker.Event.Ringing -> { Log.d(TAG, "incoming call notification"); rt.onCallRinging(event.callerName) }
            CallNotificationTracker.Event.Ended -> rt.onCallEnded()
            is CallNotificationTracker.Event.Missed -> { Log.d(TAG, "missed call notification"); rt.onMissedCall(event.texts) }
            CallNotificationTracker.Event.MissedCleared -> rt.onMissedCleared()
            null -> Unit
        }
    }

    private companion object { const val TAG = "BacklitAlerts" }
}
