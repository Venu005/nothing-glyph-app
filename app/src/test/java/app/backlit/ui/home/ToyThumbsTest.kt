package app.backlit.ui.home

import app.backlit.data.Settings
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ToyThumbsTest {
    @Test
    fun everyThumbDrawsAtBothSizes() {
        val odd = Settings(badgeMessages = "{bad json", petKind = "nope", chargeStyle = "nope", faceId = "nope")
        for (s in listOf(Settings(), odd)) for (id in ToyId.entries) for (size in listOf(25, 13)) for (t in listOf(0L, 1234L, 99_999L)) {
            val g = ToyThumbs.frame(id, s, size, 1_791_000_000_000L + t, ZoneOffset.UTC)
            assertTrue("$id $size $t", g.litCount() > 0)
            assertTrue(g.size == size)
        }
        for (size in listOf(25, 13)) {
            assertTrue(ToyThumbs.studio(size, 0).litCount() > 0)
            assertTrue(ToyThumbs.alerts(size, 300).litCount() > 0)
        }
    }

    @Test
    fun musicMoves() {
        val a = ToyThumbs.frame(ToyId.MUSIC, Settings(), 25, 0, ZoneOffset.UTC).raw().toList()
        val b = ToyThumbs.frame(ToyId.MUSIC, Settings(), 25, 400, ZoneOffset.UTC).raw().toList()
        assertTrue(a != b)
    }
}
