package app.backlit.studio

import kotlin.math.floor

/** Touch position → canvas cell. */
object EditorGeometry {
    /** The cell under (x, y), or null when the touch is outside the canvas (so strokes never paint the edge). */
    fun cellAt(x: Float, y: Float, w: Float, h: Float, n: Int): Pair<Int, Int>? {
        if (x < 0 || y < 0 || x >= w || y >= h) return null
        return floor(x / w * n).toInt().coerceIn(0, n - 1) to floor(y / h * n).toInt().coerceIn(0, n - 1)
    }

    /** The nearest cell, clamped to the canvas: for a line or circle end dragged past the edge. */
    fun clampedCellAt(x: Float, y: Float, w: Float, h: Float, n: Int): Pair<Int, Int> =
        floor(x / w * n).toInt().coerceIn(0, n - 1) to floor(y / h * n).toInt().coerceIn(0, n - 1)
}
