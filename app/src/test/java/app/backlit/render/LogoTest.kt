package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Regenerate the icon files with UPDATE_PREVIEWS=1 (see ToyPreviewsTest). */
class LogoTest {
    private val dots = LogoGeometry.dots

    @Test
    fun hasTheRingTheSparklesAndOneRedDot() {
        assertEquals(35, dots.size)
        assertEquals(1, dots.count { it.red })
        assertEquals(30, dots.count { it.r == 2.3 })
        assertEquals(4, dots.count { it.r == 1.5 })
    }

    @Test
    fun staysInsideTheAdaptiveIconSafeZone() {
        // The ring and red dot sit inside the 66 dp safe zone (r 33); the outer sparkles may reach into the 72 dp visible area (r 36).
        for (d in dots.filter { it.r != 1.5 }) assertTrue("$d", hypot(d.x - 54, d.y - 54) + d.r <= 33.0)
        for (d in dots) assertTrue("$d", hypot(d.x - 54, d.y - 54) + d.r <= 36.0)
    }

    @Test
    fun theBrightestRingDotFacesTheLight() {
        val brightest = dots.filter { it.r == 2.3 }.maxBy { it.alpha }
        val a = atan2(brightest.y - 54, brightest.x - 54)
        val diff = abs(((a - LogoGeometry.L) + 3 * PI) % (2 * PI) - PI)
        assertTrue("angle off by $diff", diff <= PI / 30 + 1e-9)
    }

    @Test
    fun monochromeHasNoRed() {
        assertFalse(LogoXml.monochrome().contains("D71921", ignoreCase = true))
        assertTrue(LogoXml.foreground().contains("D71921", ignoreCase = true))
    }

    @Test
    fun checkedInIconsAreUpToDate() {
        val update = System.getenv("UPDATE_PREVIEWS") == "1"
        for ((name, xml) in listOf("ic_launcher_foreground" to LogoXml.foreground(), "ic_launcher_monochrome" to LogoXml.monochrome())) {
            val f = File("src/main/res/drawable/$name.xml")
            if (update) f.writeText(xml) else assertEquals("$name is stale — regenerate", xml, f.readText())
        }
        val png = File("../docs/store/icon-512.png")
        if (update) { png.parentFile.mkdirs(); ImageIO.write(storeIcon(), "png", png) }
        assertTrue("store icon missing — regenerate", png.exists())
    }

    /** 512×512 Play Store icon: black square, the logo cropped to the 9..99 window. */
    private fun storeIcon(): BufferedImage {
        val img = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = java.awt.Color.BLACK
        g.fillRect(0, 0, 512, 512)
        val s = 512.0 / 90.0
        for (d in dots) {
            g.color = if (d.red) java.awt.Color(0xD7, 0x19, 0x21) else java.awt.Color(1f, 1f, 1f, d.alpha.toFloat())
            g.fill(Ellipse2D.Double((d.x - 9 - d.r) * s, (d.y - 9 - d.r) * s, 2 * d.r * s, 2 * d.r * s))
        }
        g.dispose()
        return img
    }
}
