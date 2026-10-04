package app.backlit.alerts

import app.backlit.alerts.CallNotificationTracker.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallNotificationTrackerTest {

    @Test
    fun incomingCallStartsRinging() {
        val t = CallNotificationTracker()
        assertEquals(Event.Ringing("Mom"), t.onPosted("k1", isCall = true, incoming = true, title = "Mom"))
    }

    @Test
    fun otherNotificationsAreIgnored() {
        val t = CallNotificationTracker()
        assertNull(t.onPosted("chat", isCall = false, incoming = false, title = "Hi"))
        assertNull(t.onPosted("call", isCall = true, incoming = true, title = null))
        assertNull(t.onRemoved("chat"))
    }

    @Test
    fun answeringEndsTheCall() {
        val t = CallNotificationTracker()
        t.onPosted("k1", true, true, "Mom")
        assertEquals(Event.Ended, t.onPosted("k1", isCall = true, incoming = false, title = "Mom"))   // now "ongoing"
        assertNull(t.onPosted("k1", isCall = true, incoming = false, title = "Mom"))
    }

    @Test
    fun removalEndsTheCall() {
        val t = CallNotificationTracker()
        t.onPosted("k1", true, true, "Mom")
        assertNull(t.onRemoved("other"))
        assertEquals(Event.Ended, t.onRemoved("k1"))
        assertNull(t.onRemoved("k1"))
    }

    @Test
    fun repeatedIncomingUpdatesDoNotRestart() {
        val t = CallNotificationTracker()
        t.onPosted("k1", true, true, "Mom")
        assertNull(t.onPosted("k1", true, true, "Mom"))
    }
}
