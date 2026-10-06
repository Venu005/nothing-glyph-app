package app.backlit.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertsAttentionTest {
    @Test
    fun notificationAccessComesFirst() {
        assertEquals(Attention.NOTIFICATION_ACCESS, AlertsAttention.pick(false, false, contacts = 1, devices = 1, segment = AlertsSegment.DEVICES))
        assertEquals(Attention.NOTIFICATION_ACCESS, AlertsAttention.pick(false, true, contacts = 0, devices = 0, segment = AlertsSegment.CONTACTS))
    }

    @Test
    fun nearbyOnlyWithDevices() {
        assertEquals(Attention.NEARBY, AlertsAttention.pick(true, false, contacts = 0, devices = 2, segment = AlertsSegment.ANIMATIONS))
        assertNull(AlertsAttention.pick(true, false, contacts = 0, devices = 0, segment = AlertsSegment.DEVICES))
    }

    @Test
    fun nothingWhenAllGrantedOrNotNeeded() {
        assertNull(AlertsAttention.pick(true, true, 3, 3, AlertsSegment.CONTACTS))
        assertNull(AlertsAttention.pick(false, true, contacts = 0, devices = 0, segment = AlertsSegment.ANIMATIONS))
    }
}
