package app.backlit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import app.backlit.render.PixelGrid

/** Draws a frame exactly as the panel shows it: one round LED per position. */
@Composable
fun MatrixPreview(grid: PixelGrid, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val cell = size.width / grid.size
        for (y in 0 until grid.size) for (x in 0 until grid.size) {
            if (!grid.hasLed(x, y)) continue
            val v = grid[x, y]
            val color = if (v == 0) BacklitColors.LedOff else BacklitColors.White.copy(alpha = 0.1f + 0.9f * v / 255f)
            drawCircle(color, radius = cell * 0.38f, center = Offset(x * cell + cell / 2, y * cell + cell / 2))
        }
    }
}
