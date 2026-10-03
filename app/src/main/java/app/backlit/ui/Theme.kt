package app.backlit.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.backlit.R

object BacklitColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xFF888888)
    val Line = Color(0xFF333333)
    val Red = Color(0xFFD71921)
    val LedOff = Color(0xFF1C1C1C)
}

@OptIn(ExperimentalTextApi::class)
val Doto = FontFamily(
    Font(R.font.doto, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 11.sp, letterSpacing = 2.sp),
)

@Composable
fun BacklitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BacklitColors.Black,
            surface = BacklitColors.Black,
            primary = BacklitColors.White,
            onPrimary = BacklitColors.Black,
            onBackground = BacklitColors.White,
            onSurface = BacklitColors.White,
        ),
        typography = typography,
    ) {
        // Screens are not wrapped in a Surface, so set the default text colour here.
        CompositionLocalProvider(LocalContentColor provides BacklitColors.White, content = content)
    }
}
