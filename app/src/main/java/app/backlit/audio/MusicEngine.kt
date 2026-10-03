package app.backlit.audio

import app.backlit.render.PixelGrid
import app.backlit.render.viz.IdleLine
import app.backlit.render.viz.VizStyle
import app.backlit.render.viz.VizStyles

/** Pure glue shared by the toy and the in-app preview: FFT → analysis → state → style → frame. */
class MusicEngine(private val size: Int = 25) {
    private val analyzer = SpectrumAnalyzer()
    private val state = MusicState()
    private val idle = IdleLine(size)
    private var style: VizStyle = VizStyles.create(VizStyles.ids.first(), size)

    val styleId: String get() = style.id
    val phase: MusicPhase get() = state.phase

    fun setStyle(id: String) {
        val wanted = VizStyles.normalize(id)
        if (wanted != style.id) style = VizStyles.create(wanted, size)
    }

    fun tick(
        nowMs: Long,
        dtMs: Long,
        musicActive: Boolean,
        fft: ByteArray?,
        samplingRateHz: Int,
        gain: Float,
        hasVisualizer: Boolean,
    ): PixelGrid {
        val frame = if (fft != null) analyzer.analyze(fft, samplingRateHz, dtMs, gain) else AudioFrame.SILENT
        val phase = state.update(nowMs, musicActive, hasVisualizer, frame.level)
        return when {
            phase == MusicPhase.LIVE -> { style.update(frame, dtMs); style.render() }
            state.inDecay(nowMs) -> { style.update(AudioFrame.SILENT, dtMs); style.render() }
            else -> { idle.update(dtMs, fallback = phase == MusicPhase.FALLBACK); idle.render() }
        }
    }

    fun tickDemo(dtMs: Long, frame: AudioFrame): PixelGrid {
        style.update(frame, dtMs)
        return style.render()
    }

    fun shouldRetryVisualizer(nowMs: Long): Boolean = state.shouldRetryVisualizer(nowMs)
}
