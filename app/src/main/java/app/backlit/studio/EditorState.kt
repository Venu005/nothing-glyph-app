package app.backlit.studio

/**
 * The editor's document, current frame and undo/redo history. Immutable: every edit returns a new state
 * (or the same instance when nothing changes). For a drag, the UI keeps the pointer-down state and recomputes
 * the whole stroke from it, so one stroke is one undo step.
 */
class EditorState private constructor(
    val doc: Drawing,
    val current: Int,
    private val past: List<Snap>,
    private val future: List<Snap>,
) {
    private data class Snap(val doc: Drawing, val current: Int)

    val frame: Frame get() = doc.frames[current]
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun select(i: Int) = EditorState(doc, i.coerceIn(0, doc.frames.lastIndex), past, future)

    fun undo(): EditorState {
        val p = past.lastOrNull() ?: return this
        return EditorState(p.doc, p.current, past.dropLast(1), future + Snap(doc, current))
    }

    fun redo(): EditorState {
        val f = future.lastOrNull() ?: return this
        return EditorState(f.doc, f.current, past + Snap(doc, current), future.dropLast(1))
    }

    // ── drawing ──

    fun paint(points: List<Pair<Int, Int>>, shade: Int, mirror: Boolean): EditorState {
        val n = doc.size
        val s = frame.shades.copyOf()
        val v = shade.coerceIn(0, 3).toByte()
        for ((x, y) in points) {
            if (Raster.hasLed(n, x, y)) s[y * n + x] = v
            if (mirror && Raster.hasLed(n, n - 1 - x, y)) s[y * n + (n - 1 - x)] = v
        }
        return withFrame(frame.copy(shades = s))
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, shade: Int, mirror: Boolean) = paint(Raster.line(x0, y0, x1, y1), shade, mirror)

    fun circle(cx: Int, cy: Int, r: Int, shade: Int, mirror: Boolean) = paint(Raster.circle(cx, cy, r), shade, mirror)

    fun fill(x: Int, y: Int, shade: Int, mirror: Boolean): EditorState {
        val n = doc.size
        val cells = Raster.fillRegion(n, frame.shades, x, y).toMutableSet()
        if (mirror) cells += Raster.fillRegion(n, frame.shades, n - 1 - x, y)
        return paint(cells.map { (it % n) to (it / n) }, shade, mirror = false)
    }

    fun text(text: String, shade: Int, mirror: Boolean) = paint(PixelFontText.points(PixelFontText.clean(text), doc.size), shade, mirror)

    fun shift(dx: Int, dy: Int): EditorState {
        val n = doc.size
        val s = ByteArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val sx = x - dx
            val sy = y - dy
            if (sx in 0 until n && sy in 0 until n && Raster.hasLed(n, x, y)) s[y * n + x] = frame.shades[sy * n + sx]
        }
        return withFrame(frame.copy(shades = s))
    }

    fun clear() = withFrame(frame.copy(shades = ByteArray(doc.size * doc.size)))

    // ── frames & timing ──

    fun addFrame(): EditorState {
        if (doc.frames.size >= MAX_FRAMES) return this
        val frames = doc.frames.toMutableList().apply { add(current + 1, frame.copy()) }
        return commit(doc.copy(frames = frames), current + 1)
    }

    fun deleteFrame(): EditorState {
        if (doc.frames.size <= 1) return this
        val frames = doc.frames.toMutableList().apply { removeAt(current) }
        return commit(doc.copy(frames = frames), current.coerceAtMost(frames.lastIndex))
    }

    fun moveFrame(delta: Int): EditorState {
        val to = current + delta
        if (to !in doc.frames.indices || delta == 0) return this
        val frames = doc.frames.toMutableList()
        val f = frames.removeAt(current)
        frames.add(to, f)
        return commit(doc.copy(frames = frames), to)
    }

    fun setHold(h: Int) = withFrame(frame.copy(hold = h.coerceIn(1, MAX_HOLD)))

    fun setFps(f: Int) = commit(doc.copy(fps = f.coerceIn(MIN_FPS, MAX_FPS)), current)

    fun rename(name: String) = commit(doc.copy(name = name.trim().take(MAX_NAME).trim()), current)

    private fun withFrame(f: Frame): EditorState =
        commit(doc.copy(frames = doc.frames.toMutableList().also { it[current] = f }), current)

    private fun commit(newDoc: Drawing, newCurrent: Int): EditorState {
        if (newDoc == doc && newCurrent == current) return this
        return EditorState(newDoc, newCurrent, (past + Snap(doc, current)).takeLast(HISTORY), emptyList())
    }

    companion object {
        const val HISTORY = 50
        fun of(doc: Drawing) = EditorState(doc, 0, emptyList(), emptyList())
    }
}
