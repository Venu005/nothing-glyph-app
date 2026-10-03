package app.backlit.audio

/** One analysed moment of audio: 8 bands bass→treble, overall level and a kick-drum pulse, all 0..1. */
class AudioFrame(val bands: FloatArray, val level: Float, val kick: Float) {
    init { require(bands.size == BANDS) { "expected $BANDS bands" } }

    companion object {
        const val BANDS = 8
        val SILENT = AudioFrame(FloatArray(BANDS), 0f, 0f)
    }
}
