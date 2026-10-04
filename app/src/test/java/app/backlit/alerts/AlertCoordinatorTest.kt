package app.backlit.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertCoordinatorTest {

    private val contacts = listOf(ContactRule("Mom", "builtin:heart"))
    private val devices = listOf(DeviceRule("AA:BB:CC:DD:EE:FF", "Buds", "builtin:link"))
    private fun coordinator() = AlertCoordinator({ contacts }, { devices })

    @Test
    fun importantCallerLoopsUntilEnded() {
        val c = coordinator()
        c.onCallRinging("mom", 1_000)
        assertEquals(ActiveAlert("builtin:heart", AlertKind.CALL, 1_000, 61_000), c.active)
        c.tick(30_000)
        assertEquals(AlertKind.CALL, c.active?.kind)
        c.onCallEnded()
        assertNull(c.active)
    }

    @Test
    fun callIsCappedAtSixtySeconds() {
        val c = coordinator()
        c.onCallRinging("Mom", 0)
        c.tick(59_999)
        assertEquals(AlertKind.CALL, c.active?.kind)
        c.tick(60_000)
        assertNull(c.active)
    }

    @Test
    fun otherCallersDoNothing() {
        val c = coordinator()
        c.onCallRinging("Unknown", 0)
        assertNull(c.active)
    }

    @Test
    fun deviceAlertLastsThreeSeconds() {
        val c = coordinator()
        c.onDeviceConnected("aa:bb:cc:dd:ee:ff", 10_000)
        assertEquals(ActiveAlert("builtin:link", AlertKind.DEVICE, 10_000, 13_000), c.active)
        c.tick(12_999); assertEquals(AlertKind.DEVICE, c.active?.kind)
        c.tick(13_000); assertNull(c.active)
    }

    @Test
    fun unknownDeviceDoesNothing() {
        val c = coordinator()
        c.onDeviceConnected("11:22:33:44:55:66", 0)
        assertNull(c.active)
    }

    @Test
    fun deviceCooldownThirtySeconds() {
        val c = coordinator()
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 0)
        c.tick(5_000)
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 20_000)
        assertNull(c.active)
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 30_000)
        assertEquals(AlertKind.DEVICE, c.active?.kind)
    }

    @Test
    fun callBeatsDevice() {
        val c = coordinator()
        c.onCallRinging("Mom", 0)
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 1_000)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }

    @Test
    fun callReplacesDevice() {
        val c = coordinator()
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 0)
        c.onCallRinging("Mom", 500)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }

    @Test
    fun previewPlaysForThreeSecondsButNotOverACall() {
        val c = coordinator()
        c.preview("builtin:bounce", 0)
        assertEquals(ActiveAlert("builtin:bounce", AlertKind.DEVICE, 0, 3_000), c.active)
        c.onCallRinging("Mom", 100)
        c.preview("builtin:burst", 200)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }

    @Test
    fun missedCallPlaysTenSecondsThenRemindsEveryMinute() {
        val c = coordinator()
        c.onMissedCall(listOf("Mom"), 0)
        assertEquals(ActiveAlert("builtin:heart", AlertKind.MISSED, 0, 10_000), c.active)
        assertEquals(10_000L, c.nextWakeAt())
        c.tick(10_000); assertNull(c.active)
        assertEquals(70_000L, c.nextWakeAt())
        c.tick(69_999); assertNull(c.active)
        c.tick(70_000); assertEquals(ActiveAlert("builtin:heart", AlertKind.MISSED, 70_000, 75_000), c.active)
    }

    @Test
    fun missedRemindersStopAfterTenOrWhenCleared() {
        val c = coordinator()
        c.onMissedCall(listOf("Mom"), 0)
        var t = 10_000L
        var reminders = 0
        while (t < 2_000_000) { c.tick(t); if (c.active?.kind == AlertKind.MISSED && c.active?.startedAt == t) reminders++; t += 1_000 }
        assertEquals(10, reminders)
        val d = coordinator()
        d.onMissedCall(listOf("Mom"), 0)
        d.onMissedCleared()
        assertNull(d.active); assertNull(d.nextWakeAt())
    }

    @Test
    fun unknownMissedCallerDoesNothingAndCallBeatsReminder() {
        val c = coordinator()
        c.onMissedCall(listOf("Missed call", "Stranger (1)"), 0)
        assertNull(c.active); assertNull(c.nextWakeAt())
        c.onMissedCall(listOf("Mom"), 0)
        c.onCallRinging("Mom", 5_000)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }

    @Test
    fun missedCallMatchesNameInsideNotificationText() {
        val c = coordinator()
        c.onMissedCall(listOf("Missed call", "Mom (2)"), 0)
        assertEquals(AlertKind.MISSED, c.active?.kind)
    }
}
