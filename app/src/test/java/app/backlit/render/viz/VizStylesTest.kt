package app.backlit.render.viz

import org.junit.Assert.assertEquals
import org.junit.Test

class VizStylesTest {
    @Test
    fun idsLabelsAndCycling() {
        assertEquals(listOf("mirror", "peaks", "wave"), VizStyles.ids)
        assertEquals("mirror", VizStyles.normalize("nonsense"))
        assertEquals("PEAKS", VizStyles.label("peaks"))
        assertEquals("peaks", VizStyles.next("mirror"))
        assertEquals("mirror", VizStyles.next("wave"))
        assertEquals("mirror", VizStyles.create("unknown", 25).id)
    }
}
