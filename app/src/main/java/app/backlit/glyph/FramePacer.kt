package app.backlit.glyph

/**
 * Fixed-rate frame scheduling: frames are due every [periodMs] on a fixed grid, so the time a frame
 * spends working (e.g. a slow SDK push) is taken out of the wait instead of added on top.
 * If it falls more than a whole period behind it resyncs rather than bursting to catch up.
 */
class FramePacer(private val periodMs: Long) {
    private var due = -1L

    /** Call after finishing a frame; returns how long to wait before starting the next one. */
    fun delayBeforeNext(nowMs: Long): Long {
        if (due < 0) {
            due = nowMs + periodMs
            return periodMs
        }
        due += periodMs
        if (nowMs - due >= periodMs) {
            due = nowMs
            return 0
        }
        return (due - nowMs).coerceAtLeast(0)
    }
}
