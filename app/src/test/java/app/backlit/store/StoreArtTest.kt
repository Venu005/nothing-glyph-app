package app.backlit.store

import app.backlit.badge.BadgeArt
import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.render.LogoGeometry
import app.backlit.render.PixelGrid
import app.backlit.ui.home.ToyId
import app.backlit.ui.home.ToyThumbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.time.ZoneOffset
import javax.imageio.ImageIO

/**
 * Play Store art, drawn from the app's real logo geometry and toy frames.
 *   UPDATE_PREVIEWS=1 ./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.backlit.store.StoreArtTest'
 * regenerates docs/store/feature-graphic.png and, when raw captures exist in .superpowers/store-raw/<name>.png
 * (adb screencap, git-ignored), the framed screenshots docs/store/screenshot-<n>-<name>.png.
 */
class StoreArtTest {
    private val doto = font("doto.ttf")
    private val grotesk = font("space_grotesk.ttf")
    private val dim = Color(0x9A, 0x9A, 0x9A)
    private val ledOff = Color(0x1C, 0x1C, 0x1C)

    @Test
    fun storeArtIsUpToDate() {
        val update = System.getenv("UPDATE_PREVIEWS") == "1"
        val fg = File("../docs/store/feature-graphic.png")
        if (update) ImageIO.write(featureGraphic(), "png", fg)
        assertTrue("feature graphic missing — regenerate (see class doc)", fg.exists())
        ImageIO.read(fg).let { assertEquals(1024, it.width); assertEquals(500, it.height) }
        if (update) for ((i, shot) in SHOTS.withIndex()) {
            val raw = File("../.superpowers/store-raw/${shot.first}.png").takeIf { it.exists() } ?: continue
            ImageIO.write(screenshot(ImageIO.read(raw), shot.second), "png", File("../docs/store/screenshot-${i + 1}-${shot.first}.png"))
        }
        for (f in File("../docs/store").listFiles { f -> f.name.startsWith("screenshot-") }.orEmpty()) {
            ImageIO.read(f).let { assertEquals(f.name, 1080, it.width); assertEquals(f.name, 1920, it.height) }
        }
    }

    /** Option A: the logo, BACKLIT and the tagline on the left; four live toys in a 2×2 on the right. */
    private fun featureGraphic(): BufferedImage = canvas(1024, 500) { g ->
        logo(g, 60.0, 160.0, 100.0)
        g.font = doto.deriveFont(Font.BOLD, 68f)
        g.color = Color.WHITE
        val fm = g.fontMetrics
        g.drawString("BACKLIT", 176, 210 + (fm.ascent - fm.descent) / 2)
        g.font = grotesk.deriveFont(26f)
        g.color = dim
        g.drawString("Toys and tools for your Glyph Matrix", 66, 312)
        val s = Settings()
        val badge = BadgeMessage.STARTERS.first { it.icon == "heart" }
        val frames = listOf(
            ToyThumbs.frame(ToyId.PET, s, 25, FRAME_MS, ZoneOffset.UTC),
            ToyThumbs.frame(ToyId.SAND, s, 25, FRAME_MS, ZoneOffset.UTC),
            BadgeArt.still(25, badge, BadgeText.short(badge, 0, FRAME_MS, false, ZoneOffset.UTC)),
            ToyThumbs.frame(ToyId.CLOCK, s, 25, FRAME_MS, ZoneOffset.UTC),
        )
        for ((i, f) in frames.withIndex()) disc(g, f, 580.0 + (i % 2) * 190, 70.0 + (i / 2) * 190, 5.6)
    }

    /** A 1080×1920 card: the caption in the dot font over the capture, status and navigation bars cropped. */
    private fun screenshot(raw: BufferedImage, caption: String): BufferedImage = canvas(1080, 1920) { g ->
        val top = raw.height * 55 / 1000
        val bottom = raw.height * 2 / 100
        val src = raw.getSubimage(0, top, raw.width, raw.height - top - bottom)
        g.font = doto.deriveFont(Font.BOLD, 58f)
        g.color = Color.WHITE
        val lines = wrap(g, caption, 960)
        for ((i, line) in lines.withIndex()) g.drawString(line, (1080 - g.fontMetrics.stringWidth(line)) / 2, 150 + i * 72)
        g.color = Color(0xD7, 0x19, 0x21)
        g.fill(Ellipse2D.Double(534.0, 150.0 + (lines.size - 1) * 72 + 34, 12.0, 12.0))
        val y0 = 150 + (lines.size - 1) * 72 + 90
        val h = 1920 - y0 - 60
        val w = src.width * h / src.height
        val x0 = (1080 - w) / 2
        val clip = RoundRectangle2D.Double(x0.toDouble(), y0.toDouble(), w.toDouble(), h.toDouble(), 56.0, 56.0)
        g.clip = clip
        g.drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), x0, y0, null)
        g.clip = null
        g.color = Color(0x33, 0x33, 0x33)
        g.stroke = BasicStroke(3f)
        g.draw(clip)
    }

    private fun wrap(g: Graphics2D, text: String, max: Int): List<String> {
        if (g.fontMetrics.stringWidth(text) <= max) return listOf(text)
        val words = text.split(" ")
        val out = mutableListOf("")
        for (w in words) {
            val next = if (out.last().isEmpty()) w else "${out.last()} $w"
            if (g.fontMetrics.stringWidth(next) <= max) out[out.lastIndex] = next else out += w
        }
        return out
    }

    private fun logo(g: Graphics2D, x: Double, y: Double, size: Double) {
        val s = size / 72.0   // the 18..90 window of the 108 viewport, as in the in-app logo
        for (d in LogoGeometry.dots) {
            g.color = if (d.red) Color(0xD7, 0x19, 0x21) else Color(1f, 1f, 1f, d.alpha.toFloat())
            g.fill(Ellipse2D.Double(x + (d.x - 18 - d.r) * s, y + (d.y - 18 - d.r) * s, 2 * d.r * s, 2 * d.r * s))
        }
    }

    /** Same look as MatrixPreview: one round LED per position, unlit ones dark grey. */
    private fun disc(g: Graphics2D, grid: PixelGrid, x: Double, y: Double, cell: Double) {
        for (r in 0 until grid.size) for (c in 0 until grid.size) {
            if (!grid.hasLed(c, r)) continue
            val v = grid[c, r]
            g.color = if (v == 0) ledOff else Color(1f, 1f, 1f, 0.1f + 0.9f * v / 255f)
            val rad = cell * 0.38
            g.fill(Ellipse2D.Double(x + c * cell + cell / 2 - rad, y + r * cell + cell / 2 - rad, 2 * rad, 2 * rad))
        }
    }

    private fun canvas(w: Int, h: Int, draw: (Graphics2D) -> Unit): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color.BLACK
        g.fillRect(0, 0, w, h)
        draw(g)
        g.dispose()
        return img
    }

    private fun font(name: String): Font = File("src/main/res/font/$name").inputStream().use { Font.createFont(Font.TRUETYPE_FONT, it) }

    companion object {
        /** 10:10:30 UTC on a fixed day, so the clock hands and the pet's bob land on the same frame every run. */
        const val FRAME_MS = 1_790_000_000_000L + 10 * 3_600_000L + 10 * 60_000L + 30_000L - (1_790_000_000_000L % 86_400_000L)

        val SHOTS = listOf(
            "home" to "Seven toys for your Glyph",
            "pet" to "A pet that lives on the back",
            "sand" to "Flip it. It's an hourglass.",
            "badge" to "Leave a message face-down",
            "studio" to "Draw your own animations",
            "alerts" to "Know who's calling and what's connected",
        )
    }
}
