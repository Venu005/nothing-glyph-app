package app.backlit.audio

enum class MusicPhase { IDLE, LIVE, FALLBACK }

/** Decides what the Music toy shows: idle line, live visualizer, or the fallback line. */
class MusicState {
    var phase: MusicPhase = MusicPhase.IDLE
        private set

    private var silentSince = -1L
    private var idleSince = -1L
    private var lastRetryAt = 0L

    fun update(nowMs: Long, musicActive: Boolean, hasVisualizer: Boolean, level: Float): MusicPhase {
        when {
            !musicActive -> {
                if (phase == MusicPhase.LIVE) idleSince = nowMs
                go(MusicPhase.IDLE, nowMs)
                silentSince = -1L
            }
            !hasVisualizer -> go(MusicPhase.FALLBACK, nowMs)
            level > 0f -> {
                silentSince = -1L
                go(MusicPhase.LIVE, nowMs)
            }
            else -> {
                if (silentSince < 0) silentSince = nowMs
                if (nowMs - silentSince >= SILENCE_TO_FALLBACK_MS) go(MusicPhase.FALLBACK, nowMs)
            }
        }
        return phase
    }

    /** True for 1 s after music stops, so the style can let its bars fall before the idle line. */
    fun inDecay(nowMs: Long): Boolean =
        phase == MusicPhase.IDLE && idleSince >= 0 && nowMs - idleSince < DECAY_MS

    fun shouldRetryVisualizer(nowMs: Long): Boolean {
        if (phase != MusicPhase.FALLBACK || nowMs - lastRetryAt < RETRY_MS) return false
        lastRetryAt = nowMs
        return true
    }

    private fun go(next: MusicPhase, nowMs: Long) {
        if (next == MusicPhase.FALLBACK && phase != MusicPhase.FALLBACK) lastRetryAt = nowMs
        phase = next
    }

    private companion object {
        const val SILENCE_TO_FALLBACK_MS = 5_000L
        const val DECAY_MS = 1_000L
        const val RETRY_MS = 10_000L
    }
}
