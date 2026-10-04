package app.backlit.alerts

import app.backlit.anim.Bounce
import app.backlit.anim.Heartbeat
import app.backlit.anim.Link
import app.backlit.anim.MuseumFormat
import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnimationLibraryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sample(id: String) = (MuseumFormat.parse(
        """{"v":4,"frames":[{"d":120,"p":[${List(PixelGrid.ledCount(13)) { 255 }.joinToString(",")}]}]}""", id, "Sample",
    ) as MuseumFormat.Result.Ok).animation

    @Test
    fun savesLoadsAndDeletes() {
        val lib = AnimationLibrary(tmp.root.resolve("animations"))
        val a = sample("import:abc-123")
        lib.save(a)
        val entry = AnimIndexEntry("import:abc-123", "Sample", 4)
        val fresh = AnimationLibrary(tmp.root.resolve("animations"))     // no cache: reads the file
        assertEquals(a.frames13, fresh.load(entry)?.frames13)
        fresh.delete("import:abc-123")
        assertNull(AnimationLibrary(tmp.root.resolve("animations")).load(entry))
    }

    @Test
    fun resolvesBuiltInsAndImports() {
        val lib = AnimationLibrary(tmp.root)
        lib.save(sample("import:x"))
        val imports = listOf(AnimIndexEntry("import:x", "Sample", 4))
        assertEquals(Bounce, lib.resolve("builtin:bounce", imports, "builtin:heart"))
        assertEquals("import:x", lib.resolve("import:x", imports, "builtin:heart").id)
    }

    @Test
    fun missingImportFallsBack() {
        val lib = AnimationLibrary(tmp.root)
        assertEquals(Heartbeat, lib.resolve("import:gone", emptyList(), "builtin:heart"))
        assertEquals(Link, lib.resolve("import:gone", listOf(AnimIndexEntry("import:gone", "Gone", 1)), "builtin:link"))
    }
}
