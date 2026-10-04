package app.backlit.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeSessionTest {

    private var target = 80
    private fun session() = ChargeSession { target }
    private fun on(level: Int) = Battery(plugged = true, level = level)
    private fun off(level: Int) = Battery(plugged = false, level = level)

    @Test
    fun plugInThenCharging() {
        val s = session(); s.onBind(off(40), 0)
        assertEquals(Moment.STILL, s.show.moment)
        s.onBattery(on(40), 1000, active = true)
        assertEquals(Show(Moment.PLUG_IN, 1000), s.show)
        assertEquals(6000L, s.nextWakeAt())
        s.tick(5999); assertEquals(Moment.PLUG_IN, s.show.moment)
        s.tick(6000); assertEquals(Moment.CHARGING, s.show.moment)
        assertNull(s.nextWakeAt())
    }

    @Test
    fun crossingPlaysDoneOnceThenCharging() {
        val s = session(); s.onBind(on(78), 0)
        s.onBattery(on(79), 10, true); assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(80), 20, true); assertEquals(Show(Moment.DONE, 20), s.show)
        s.tick(20 + 3500); assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(79), 5000, true); s.onBattery(on(80), 6000, true)   // jitter
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun crossingDuringPlugInIsQueued() {
        val s = session(); s.onBind(off(79), 0)
        s.onBattery(on(79), 0, true)
        s.onBattery(on(80), 1000, true)
        assertEquals(Moment.PLUG_IN, s.show.moment)
        s.tick(5000); assertEquals(Show(Moment.DONE, 5000), s.show)
    }

    @Test
    fun lateTickStillPlaysFullDone() {
        val s = session(); s.onBind(off(79), 0)
        s.onBattery(on(79), 0, true); s.onBattery(on(80), 100, true)
        s.tick(30_000)                                   // a long alert covered the plug-in
        assertEquals(Show(Moment.DONE, 30_000), s.show)
    }

    @Test
    fun unplugGoesStillFromEveryMoment() {
        val s = session(); s.onBind(off(50), 0)
        s.onBattery(on(50), 0, true); s.onBattery(off(50), 100, true)
        assertEquals(Moment.STILL, s.show.moment)
        s.onBattery(on(79), 200, true); s.tick(6000); s.onBattery(on(80), 7000, true)
        assertEquals(Moment.DONE, s.show.moment)
        s.onBattery(off(80), 7100, true); assertEquals(Moment.STILL, s.show.moment)
    }

    @Test
    fun bindWhileChargingAboveTargetNeverPlaysDone() {
        val s = session(); s.onBind(on(85), 0)
        assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(86), 10, true); assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun plugInAlreadyAboveTargetNeverPlaysDone() {
        val s = session(); s.onBind(off(80), 0)
        s.onBattery(on(80), 0, true); s.tick(5000)
        s.onBattery(on(81), 6000, true)
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun replugStartsANewSession() {
        val s = session(); s.onBind(on(79), 0)
        s.onBattery(on(80), 10, true); s.tick(4000)
        s.onBattery(off(80), 5000, true)
        s.onBattery(off(75), 6000, true)
        s.onBattery(on(75), 7000, true); s.tick(12_000)
        s.onBattery(on(80), 13_000, true)
        assertEquals(Moment.DONE, s.show.moment)
    }

    @Test
    fun loweringTargetBelowLevelDoesNotFire() {
        val s = session(); target = 100
        s.onBind(on(70), 0)
        target = 70
        s.onBattery(on(71), 10, true)
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun aodPlugSkipsPlugInAndAodCrossingIsConsumed() {
        val s = session(); s.onBind(off(79), 0)
        s.onBattery(on(79), 0, active = false)
        assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(80), 10, active = false)
        assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(79), 20, true); s.onBattery(on(80), 30, true)
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun repeatedIdenticalUpdatesChangeNothing() {
        val s = session(); s.onBind(off(40), 0)
        s.onBattery(on(40), 0, true)
        repeat(20) { s.onBattery(on(40), 100L + it, true) }
        assertEquals(Show(Moment.PLUG_IN, 0), s.show)
    }

    @Test
    fun bindSoonAfterPlugReplaysPlugIn() {
        // Nothing's own charge animation holds the matrix for a few seconds after plugging in, then hands back.
        val s = session(); s.onBind(on(62), now = 20_000, pluggedAt = 11_000)
        assertEquals(Show(Moment.PLUG_IN, 20_000), s.show)
        s.tick(25_000); assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun bindLongAfterPlugGoesStraightToCharging() {
        val s = session(); s.onBind(on(62), now = 40_000, pluggedAt = 11_000)
        assertEquals(Moment.CHARGING, s.show.moment)
        val u = session(); u.onBind(off(62), now = 12_000, pluggedAt = 11_000)   // unplugged again since
        assertEquals(Moment.STILL, u.show.moment)
    }

    @Test
    fun replayedPlugInStillQueuesDoneOnCrossing() {
        val s = session(); s.onBind(on(79), now = 10_000, pluggedAt = 5_000)
        s.onBattery(on(80), 11_000, true)
        s.tick(15_000); assertEquals(Moment.DONE, s.show.moment)
    }
}
