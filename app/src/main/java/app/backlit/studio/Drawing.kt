package app.backlit.studio

const val MAX_FRAMES = 24
const val MIN_FPS = 2
const val MAX_FPS = 20
const val DEFAULT_FPS = 8
const val MAX_HOLD = 4
const val MAX_NAME = 24
val SHADE_VALUES = intArrayOf(0, 70, 150, 255)

/** One frame: a shade index (0 off, 1 dim, 2 med, 3 bright) per cell, row-major, plus how many frame-times it holds. */
class Frame(val shades: ByteArray, val hold: Int) {
    operator fun get(size: Int, x: Int, y: Int): Int = shades[y * size + x].toInt()
    fun copy(shades: ByteArray = this.shades.copyOf(), hold: Int = this.hold) = Frame(shades, hold)
    override fun equals(other: Any?) = other is Frame && other.hold == hold && other.shades.contentEquals(shades)
    override fun hashCode() = 31 * hold + shades.contentHashCode()
}

data class Drawing(val name: String, val size: Int, val fps: Int, val frames: List<Frame>) {
    companion object {
        fun blank(size: Int, name: String = "") = Drawing(name, size, DEFAULT_FPS, listOf(Frame(ByteArray(size * size), 1)))
    }
}
