package app.backlit.studio

import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import app.backlit.render.PixelGrid
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Drawing ↔ ImportedAnimation (the Glyph Museum representation the library stores). */
object DrawingCodec {
    data class Decoded(val drawing: Drawing, val truncated: Boolean, val simplified: Boolean)

    fun shadeOf(v: Int): Int = when {
        v < 35 -> 0
        v < 110 -> 1
        v < 203 -> 2
        else -> 3
    }

    fun frameMs(fps: Int): Long = (1000.0 / fps.coerceIn(MIN_FPS, MAX_FPS)).roundToLong()

    fun grid(d: Drawing, frame: Frame): PixelGrid {
        val g = PixelGrid(d.size)
        for (y in 0 until d.size) for (x in 0 until d.size) g.put(x, y, SHADE_VALUES[frame[d.size, x, y]])
        return g
    }

    fun encode(d: Drawing, id: String): ImportedAnimation {
        val src = d.frames.map { grid(d, it) }
        val other = if (d.size >= 25) 13 else 25
        val converted = src.map { MuseumFormat.resample(it, other) }
        return ImportedAnimation(
            id, d.name, d.size,
            frames25 = if (d.size >= 25) src else converted,
            frames13 = if (d.size >= 25) converted else src,
            durations = d.frames.map { it.hold * frameMs(d.fps) },
        )
    }

    fun decode(anim: ImportedAnimation, name: String, fps: Int, size: Int): Decoded {
        val all = if (size >= 25) anim.frames25 else anim.frames13
        val truncated = all.size > MAX_FRAMES
        val frames = all.take(MAX_FRAMES)
        val durations = anim.durations.take(MAX_FRAMES)
        val useFps = if (fps > 0) fps.coerceIn(MIN_FPS, MAX_FPS)
        else (1000.0 / durations.min()).roundToInt().coerceIn(MIN_FPS, MAX_FPS)
        val ms = frameMs(useFps)
        var simplified = false
        val out = frames.mapIndexed { i, g ->
            val shades = ByteArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                if (!g.hasLed(x, y)) continue
                val v = g[x, y]
                if (v !in SHADE_VALUES) simplified = true
                shades[y * size + x] = shadeOf(v).toByte()
            }
            Frame(shades, (durations[i].toDouble() / ms).roundToInt().coerceIn(1, MAX_HOLD))
        }
        return Decoded(Drawing(name.trim().take(MAX_NAME), size, useFps, out), truncated, simplified)
    }
}
