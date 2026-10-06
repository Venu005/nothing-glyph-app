package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class PetBrainTest {
    private val t0 = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()
    private val night = Instant.parse("2026-10-05T23:30:00Z").toEpochMilli()
    private val alwaysBoo = object : Random() { override fun nextBits(bitCount: Int) = 0 }
    private val neverBoo = object : Random() { override fun nextBits(bitCount: Int) = -1 ushr (32 - bitCount) }   // nextInt(5) = 2
    private fun brain(mood: Double, at: Long = t0, random: Random = neverBoo) =
        PetBrain(MoodState(mood, at), { SleepWindow(23, 7) }, ZoneOffset.UTC, random)

    @Test
    fun baseFollowsSleepChargingAndMood() {
        assertEquals(Base.HAPPY, brain(80.0).base(t0)); assertEquals(Base.CONTENT, brain(50.0).base(t0))
        assertEquals(Base.BORED, brain(20.0).base(t0)); assertEquals(Base.SAD, brain(5.0).base(t0))
        assertEquals(Base.ASLEEP, brain(80.0, night).base(night))
        val b = brain(80.0); b.onCharging(true, 40, t0); assertEquals(Base.MUNCH, b.base(t0))
    }

    @Test
    fun petCooldown() {
        val b = brain(50.0)
        b.onLongPress(t0, true); assertEquals(Reaction.PET, b.reaction(t0)); assertEquals(65, b.mood(t0))
        b.onLongPress(t0 + 10_000, true); assertEquals(Reaction.PET, b.reaction(t0 + 10_000)); assertEquals(65, b.mood(t0 + 10_000))
        b.onLongPress(t0 + 31_000, true); assertEquals(80, b.mood(t0 + 31_000))
        assertNull(b.reaction(t0 + 31_000 + 2600))
    }

    @Test
    fun yawnAtNightKeepsHimAwakeAMinute() {
        val b = brain(50.0, night)
        b.onLongPress(night, true)
        assertEquals(Reaction.YAWN, b.reaction(night)); assertEquals(55, b.mood(night))
        assertEquals(Base.CONTENT, b.base(night + 4_000))
        assertEquals(Base.ASLEEP, b.base(night + 61_000))
    }

    @Test
    fun shakesDizzyThenAngryThenCalmed() {
        val b = brain(50.0)
        b.onShake(5f, t0, true); assertNull(b.reaction(t0))                         // not a big shake
        b.onShake(15f, t0, true); assertEquals(Reaction.DIZZY, b.reaction(t0)); assertEquals(47, b.mood(t0))
        b.onShake(15f, t0 + 1_000, true); assertEquals(47, b.mood(t0 + 1_000))     // loss cooldown
        b.onShake(15f, t0 + 2_000, true); assertEquals(Reaction.ANGRY, b.reaction(t0 + 2_000)); assertEquals(42, b.mood(t0 + 2_000))
        b.onLongPress(t0 + 3_000, true); assertEquals(Reaction.CALMED, b.reaction(t0 + 3_000)); assertEquals(52, b.mood(t0 + 3_000))
    }

    @Test
    fun onlyCalmedReplacesAngry() {
        val b = brain(50.0)
        repeat(3) { b.onShake(15f, t0 + it * 100L, true) }
        assertEquals(Reaction.ANGRY, b.reaction(t0 + 300))
        b.onGravity(0f, 0f, 9.8f, t0 + 400, true); b.onGravity(0f, 0f, 9.8f, t0 + 2_500, true)
        b.onGravity(0f, 0f, -9.8f, t0 + 2_600, true); b.onGravity(0f, 0f, -9.8f, t0 + 3_200, true)   // a peek would fire here
        assertEquals(Reaction.ANGRY, b.reaction(t0 + 3_200))
    }

    @Test
    fun peekOnlyOncePerFaceDown() {
        val b = brain(50.0)
        b.onGravity(0f, 0f, 9.8f, t0, true); b.onGravity(0f, 0f, 9.8f, t0 + 2_500, true)
        b.onGravity(0f, 0f, -9.8f, t0 + 2_600, true); assertNull(b.reaction(t0 + 2_600))
        b.onGravity(0f, 0f, -9.8f, t0 + 3_200, true); assertEquals(Reaction.PEEK, b.reaction(t0 + 3_200)); assertEquals(55, b.mood(t0 + 3_200))
        b.onGravity(0f, 0f, -9.8f, t0 + 9_000, true); assertNull(b.reaction(t0 + 9_000))            // still face-down: no repeat
    }

    @Test
    fun quickFlipDoesNotPeek() {
        val b = brain(50.0)
        b.onGravity(0f, 0f, 9.8f, t0, true); b.onGravity(0f, 0f, 9.8f, t0 + 500, true)
        b.onGravity(0f, 0f, -9.8f, t0 + 600, true); b.onGravity(0f, 0f, -9.8f, t0 + 1_300, true)
        assertNull(b.reaction(t0 + 1_300))
    }

    @Test
    fun chargingAddsAPointPerMinuteWhileBound() {
        val b = brain(50.0)
        b.onCharging(true, 40, t0)
        b.tick(t0 + 150_000, true)
        assertEquals(52, b.mood(t0 + 150_000))       // +2 minutes, −0.42 decay
        assertEquals(40, b.pose(t0 + 150_000, 25).level)
    }

    @Test
    fun aodEventsChangeMoodOnly() {
        val b = brain(50.0)
        b.onLongPress(t0, active = false)
        assertNull(b.reaction(t0)); assertEquals(65, b.mood(t0))
        b.onShake(15f, t0 + 100, false); assertNull(b.reaction(t0 + 100))
    }

    @Test
    fun booOnlyWhenHappyActiveAndRarely() {
        val b = brain(90.0, random = alwaysBoo)
        b.tick(t0, false); assertNull(b.reaction(t0))                               // AOD: no boo
        b.tick(t0 + 1, true); assertEquals(Reaction.BOO, b.reaction(t0 + 1))
        b.tick(t0 + 120_000, true); assertNull(b.reaction(t0 + 120_000))            // within 10 min
        b.tick(t0 + 601_000, true); assertEquals(Reaction.BOO, b.reaction(t0 + 601_000))
        val c = brain(50.0, random = alwaysBoo); c.tick(t0, true); assertNull(c.reaction(t0))   // not happy
    }

    @Test
    fun tiltLooksAndLeans() {
        val b = brain(50.0)
        b.onGravity(9.8f, -9.8f, 0f, t0, true)
        val p = b.pose(t0, 25)
        assertEquals(1, p.lookX); assertEquals(-1, p.lookY); assertEquals(1, p.lean)
        assertEquals(0, b.pose(t0, 13).lookY)
        b.onLongPress(t0, true)
        assertEquals(0, b.pose(t0, 25).lookX)                                     // no tilt while reacting
    }

    @Test
    fun snapshotClearsDirty() {
        val b = brain(50.0)
        assertFalse(b.dirty)
        b.onLongPress(t0, true); assertTrue(b.dirty)
        val s = b.snapshot(t0)
        assertEquals(65.0, s.mood, 1e-9); assertEquals(t0, s.at); assertFalse(b.dirty)
    }
    @Test
    fun calmingOnlyRewardsOnceEveryTenMinutes() {
        val b = brain(50.0)
        fun anger(at: Long) { b.onShake(15f, at, true); b.onShake(15f, at + 1_000, true); b.onShake(15f, at + 2_000, true) }
        anger(t0); b.onLongPress(t0 + 3_000, true)
        val afterFirst = b.moodExact(t0 + 3_000)
        anger(t0 + 20_000); b.onLongPress(t0 + 23_000, true)
        assertEquals("second calm", Reaction.CALMED, b.reaction(t0 + 23_000))
        assertTrue("no second +10: ${b.moodExact(t0 + 23_000)} vs $afterFirst", b.moodExact(t0 + 23_000) < afterFirst)
        anger(t0 + 700_000)
        val before = b.moodExact(t0 + 703_000)
        b.onLongPress(t0 + 703_000, true)
        assertEquals("third calm", Reaction.CALMED, b.reaction(t0 + 703_000))
        assertEquals("10 min later the +10 counts again", before + 10.0, b.moodExact(t0 + 703_000), 0.01)
    }
}
