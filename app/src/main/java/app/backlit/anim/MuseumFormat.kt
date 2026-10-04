package app.backlit.anim

import app.backlit.render.PixelGrid
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.math.roundToInt

/** A frame-based animation imported from Glyph Museum, prepared for both grid sizes. */
class ImportedAnimation(
    override val id: String,
    override val name: String,
    val sourceSize: Int,
    val frames25: List<PixelGrid>,
    val frames13: List<PixelGrid>,
    val durations: List<Long>,
) : GlyphAnimation {
    override val loopMs: Long = durations.sum().coerceAtLeast(1)

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val frames = if (size >= 25) frames25 else frames13
        var t = ((tMs % loopMs) + loopMs) % loopMs
        for (i in durations.indices) {
            if (t < durations[i]) return frames[i]
            t -= durations[i]
        }
        return frames.last()
    }
}

/**
 * Glyph Museum / Glyph Matrix Editor JSON: {"v":1|4,"frames":[{"d":ms?,"p":[0..255 per LED]}]}.
 * LEDs are listed row by row, only positions inside the round mask (same as PixelGrid.hasLed).
 * Format learned by reading the editor's source; no code copied.
 */
object MuseumFormat {
    sealed interface Result {
        data class Ok(val animation: ImportedAnimation) : Result
        data object Invalid : Result
    }

    @Serializable private data class FileJson(val v: Int, val frames: List<FrameJson>)
    @Serializable private data class FrameJson(val d: Int? = null, val p: List<Int>)

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_FRAMES = 600

    fun parse(text: String, id: String, name: String): Result {
        val file = runCatching { json.decodeFromString<FileJson>(text) }.getOrNull() ?: return Result.Invalid
        val size = when (file.v) { 1 -> 25; 4 -> 13; else -> return Result.Invalid }
        if (file.frames.isEmpty() || file.frames.size > MAX_FRAMES) return Result.Invalid
        val count = PixelGrid.ledCount(size)
        if (file.frames.any { it.p.size != count }) return Result.Invalid
        val source = file.frames.map { unpack(it.p, size) }
        val durations = file.frames.map { (it.d ?: 100).coerceIn(20, 5000).toLong() }
        val other = if (size == 25) 13 else 25
        val converted = source.map { resample(it, other) }
        return Result.Ok(
            ImportedAnimation(
                id, name, size,
                frames25 = if (size == 25) source else converted,
                frames13 = if (size == 13) source else converted,
                durations = durations,
            ),
        )
    }

    fun toJson(anim: ImportedAnimation): String {
        val frames = if (anim.sourceSize == 25) anim.frames25 else anim.frames13
        val file = FileJson(
            v = if (anim.sourceSize == 25) 1 else 4,
            frames = frames.mapIndexed { i, g -> FrameJson(anim.durations[i].toInt(), pack(g)) },
        )
        return json.encodeToString(FileJson.serializer(), file)
    }

    private fun unpack(values: List<Int>, size: Int): PixelGrid {
        val g = PixelGrid(size)
        var i = 0
        for (y in 0 until size) for (x in 0 until size) {
            if (g.hasLed(x, y)) g.put(x, y, values[i++].coerceIn(0, 255))
        }
        return g
    }

    private fun pack(g: PixelGrid): List<Int> {
        val out = ArrayList<Int>(PixelGrid.ledCount(g.size))
        for (y in 0 until g.size) for (x in 0 until g.size) if (g.hasLed(x, y)) out += g[x, y]
        return out
    }

    /** Area-average resampling: each target LED averages the source pixels whose centres fall in its box. */
    fun resample(src: PixelGrid, size: Int): PixelGrid {
        val n = src.size
        val g = PixelGrid(size)
        val scale = n.toDouble() / size
        for (ty in 0 until size) for (tx in 0 until size) {
            if (!g.hasLed(tx, ty)) continue
            var sum = 0
            var count = 0
            for (sy in 0 until n) for (sx in 0 until n) {
                val cx = sx + 0.5
                val cy = sy + 0.5
                if (cx >= tx * scale && cx < (tx + 1) * scale && cy >= ty * scale && cy < (ty + 1) * scale) {
                    sum += src[sx, sy]; count++
                }
            }
            val v = if (count > 0) (sum.toDouble() / count).roundToInt()
            else src[floor((tx + 0.5) * scale).toInt(), floor((ty + 0.5) * scale).toInt()]
            g.put(tx, ty, v)
        }
        return g
    }
}
