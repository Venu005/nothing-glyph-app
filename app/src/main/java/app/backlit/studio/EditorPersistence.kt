package app.backlit.studio

import java.nio.ByteBuffer

/** Compact bytes for a Drawing, so the editor's document survives activity recreation (saved instance state). */
object DrawingBytes {
    private const val MAGIC = 0x42445731 // "BDW1"

    fun encode(d: Drawing): ByteArray {
        val name = d.name.toByteArray(Charsets.UTF_8)
        val cells = d.size * d.size
        val buf = ByteBuffer.allocate(4 * 5 + name.size + d.frames.size * (4 + cells))
        buf.putInt(MAGIC).putInt(d.size).putInt(d.fps).putInt(d.frames.size).putInt(name.size).put(name)
        for (f in d.frames) buf.putInt(f.hold).put(f.shades)
        return buf.array()
    }

    fun decode(bytes: ByteArray): Drawing? = runCatching {
        val buf = ByteBuffer.wrap(bytes)
        require(buf.int == MAGIC)
        val size = buf.int
        require(size == 25 || size == 13)
        val fps = buf.int
        val count = buf.int
        require(count in 1..MAX_FRAMES)
        val name = ByteArray(buf.int).also { buf.get(it) }.toString(Charsets.UTF_8)
        val frames = List(count) { Frame(hold = buf.int, shades = ByteArray(size * size).also { buf.get(it) }) }
        Drawing(name, size, fps, frames)
    }.getOrNull()
}

/**
 * Save bookkeeping for the editor. The id is fixed when a save starts, so a second SAVE tap while the first
 * write is in flight updates the same drawing; "dirty" compares against what the last finished save wrote.
 */
class SaveTracker(id: String?, saved: Drawing?) {
    var id: String? = id
        private set
    var saved: Drawing? = saved
        private set

    fun begin(doc: Drawing, newId: () -> String): String = id ?: newId().also { id = it }

    private var base: Drawing? = null

    fun finished(doc: Drawing) { saved = doc }

    /** What a brand-new drawing starts as, so leaving it untouched isn't a change to discard. */
    fun baseline(doc: Drawing) { base = doc }

    fun dirty(doc: Drawing): Boolean = doc != (saved ?: base)
}
