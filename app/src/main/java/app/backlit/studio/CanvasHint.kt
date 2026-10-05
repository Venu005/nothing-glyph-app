package app.backlit.studio

import app.backlit.render.PixelGrid

/** What the Canvas toy shows: which drawing, the next one on long press, and a hint when there are none. */
object CanvasHint {
    /** A small dotted pencil, pointing down-left. */
    fun frame(size: Int): PixelGrid {
        val g = PixelGrid(size)
        if (size >= 25) {
            for (i in 0..8 step 2) g.put(8 + i, 16 - i, 150)                  // dotted body
            g.put(6, 18, 255); g.put(7, 17, 70)                               // tip
            g.put(17, 7, 255); g.put(18, 6, 255)                              // eraser end
        } else {
            for (i in 0..4 step 2) g.put(4 + i, 8 - i, 150)
            g.put(3, 9, 255)
            g.put(9, 3, 255); g.put(10, 2, 255)
        }
        return g
    }

    fun pick(chosenId: String, ids: List<String>): String? = if (chosenId in ids) chosenId else ids.firstOrNull()

    fun next(chosenId: String, ids: List<String>): String? {
        val cur = pick(chosenId, ids) ?: return null
        return ids[(ids.indexOf(cur) + 1) % ids.size]
    }
}
