package app.backlit.pet

import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Base { HAPPY, CONTENT, BORED, SAD, ASLEEP, MUNCH }

enum class Reaction(val ms: Long) { PET(2600), YAWN(3600), DIZZY(2000), ANGRY(6000), CALMED(4000), PEEK(3900), BOO(2600) }

data class Pose(
    val base: Base,
    val reaction: Reaction?,
    val reactionStart: Long,
    val lookX: Int,
    val lookY: Int,
    val lean: Int,
    val level: Int,
)

/**
 * The pet's state machine. Pure: callers pass wall-clock time and whether the toy is ACTIVE (screen on) —
 * events in AOD only change mood, never start a reaction.
 */
class PetBrain(
    initial: MoodState,
    private val sleep: () -> SleepWindow,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val random: Random = Random.Default,
) {
    private var mood = PetMood.clamp(initial.mood)
    private var moodAt = initial.at
    var dirty = false
        private set

    private var reaction: Reaction? = null
    private var reactionStart = 0L
    private var lastPetGain = Long.MIN_VALUE / 2
    private var lastShakeLoss = Long.MIN_VALUE / 2
    private var lastDizzy = Long.MIN_VALUE / 2
    private val bigShakes = ArrayDeque<Long>()
    private var awakeUntil = 0L
    private var charging = false
    private var level = 0
    private var chargeAt = 0L
    private var chargeAccum = 0L
    private var faceDownSince: Long? = null
    private var notDownSince: Long? = null
    private var peekArmed = false
    private var gx = 0f
    private var gy = 0f
    private var lastBoo = Long.MIN_VALUE / 2
    private var nextBooCheck = Long.MIN_VALUE / 2

    private fun current(now: Long) = PetMood.decay(MoodState(mood, moodAt), now, sleep(), zone)

    private fun change(now: Long, delta: Double) {
        mood = PetMood.clamp(current(now) + delta)
        moodAt = now
        dirty = true
    }

    fun mood(now: Long): Int = current(now).roundToInt()

    /** The fractional mood, for countdown hints. */
    fun moodExact(now: Long): Double = current(now)

    fun snapshot(now: Long): MoodState {
        mood = current(now)
        moodAt = now
        dirty = false
        return MoodState(mood, now)
    }

    private fun asleep(now: Long) = sleep().contains(now, zone) && now >= awakeUntil

    fun base(now: Long): Base {
        val m = mood(now)
        return when {
            asleep(now) -> Base.ASLEEP
            charging -> Base.MUNCH
            m >= 70 -> Base.HAPPY
            m >= 40 -> Base.CONTENT
            m >= 15 -> Base.BORED
            else -> Base.SAD
        }
    }

    fun reaction(now: Long): Reaction? {
        val r = reaction ?: return null
        return if (now - reactionStart < r.ms) r else null
    }

    private fun start(r: Reaction, now: Long, active: Boolean) {
        if (!active) return
        val cur = reaction(now)
        if (cur == Reaction.ANGRY && r != Reaction.CALMED && r != Reaction.ANGRY) return
        reaction = r
        reactionStart = now
    }

    fun onLongPress(now: Long, active: Boolean) {
        if (reaction(now) == Reaction.ANGRY) {
            start(Reaction.CALMED, now, active)
            change(now, 10.0)
            return
        }
        if (asleep(now)) {
            awakeUntil = now + 60_000
            start(Reaction.YAWN, now, active)
            change(now, 5.0)
            return
        }
        start(Reaction.PET, now, active)
        if (now - lastPetGain >= 30_000) {
            lastPetGain = now
            change(now, 15.0)
        }
    }

    fun onShake(magnitude: Float, now: Long, active: Boolean) {
        if (magnitude < 12f) return
        bigShakes.addLast(now)
        while (bigShakes.isNotEmpty() && now - bigShakes.first() > 10_000) bigShakes.removeFirst()
        if (bigShakes.size >= 3) {
            bigShakes.clear()
            start(Reaction.ANGRY, now, active)
            change(now, -5.0)
            return
        }
        if (now - lastDizzy >= 1_500) {
            lastDizzy = now
            start(Reaction.DIZZY, now, active)
        }
        if (now - lastShakeLoss >= 10_000) {
            lastShakeLoss = now
            change(now, -3.0)
        }
    }

    fun onGravity(x: Float, y: Float, z: Float, now: Long, active: Boolean) {
        gx = x
        gy = y
        if (z <= -7f) {
            notDownSince = null
            val since = faceDownSince ?: now.also { faceDownSince = it }
            if (peekArmed && now - since >= 500) {
                peekArmed = false
                start(Reaction.PEEK, now, active)
                change(now, 5.0)
            }
        } else {
            faceDownSince = null
            val since = notDownSince ?: now.also { notDownSince = it }
            if (now - since >= 2_000) peekArmed = true
        }
    }

    fun onCharging(on: Boolean, level: Int, now: Long) {
        this.level = level.coerceIn(0, 100)
        if (on && !charging) chargeAt = now
        if (!on && charging) chargeAccum = 0
        charging = on
    }

    fun tick(now: Long, active: Boolean) {
        if (charging) {
            chargeAccum += (now - chargeAt).coerceAtLeast(0)
            chargeAt = now
            val minutes = chargeAccum / 60_000
            if (minutes > 0) {
                chargeAccum -= minutes * 60_000
                change(now, minutes.toDouble())
            }
        }
        if (active && reaction(now) == null && base(now) == Base.HAPPY && now - lastBoo >= 600_000 && now >= nextBooCheck) {
            nextBooCheck = now + 60_000
            if (random.nextInt(5) == 0) {
                lastBoo = now
                start(Reaction.BOO, now, active)
            }
        }
    }

    fun pose(now: Long, size: Int): Pose {
        val r = reaction(now)
        val still = r != null
        val lx = if (still) 0 else (gx / 9.8f).roundToInt().coerceIn(-1, 1)
        val ly = if (still || size < 25) 0 else (gy / 9.8f).roundToInt().coerceIn(-1, 1)
        val lean = if (still || abs(gx) <= 4.9f) 0 else if (gx > 0) 1 else -1
        return Pose(base(now), r, reactionStart, lx, ly, lean, level)
    }
}
