package app.backlit.glyph

import app.backlit.render.Mode

/**
 * The SDK does not say whether a toy is showing in the carousel or as the always-on toy.
 * EVENT_AOD arrives once a minute only in AOD, so a recent EVENT_AOD means AOD.
 */
class ModeTracker(private val aodOnly: Boolean) {
    private var lastAodAt: Long = -1L

    fun onAodEvent(nowMillis: Long) {
        lastAodAt = nowMillis
    }

    fun mode(nowMillis: Long): Mode = when {
        aodOnly -> Mode.AOD
        lastAodAt >= 0 && nowMillis - lastAodAt < 70_000L -> Mode.AOD
        else -> Mode.ACTIVE
    }
}
