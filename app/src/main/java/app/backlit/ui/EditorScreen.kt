package app.backlit.ui

import androidx.compose.runtime.Composable
import app.backlit.glyph.DeviceProfile

@Composable
fun EditorScreen(drawingId: String?, profile: DeviceProfile, onClose: () -> Unit) {
    ScreenHeader("STUDIO", onBack = onClose)
}
