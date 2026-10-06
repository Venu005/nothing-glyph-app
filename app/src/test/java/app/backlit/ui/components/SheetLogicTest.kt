package app.backlit.ui.components

import app.backlit.data.Settings
import app.backlit.ui.toys.ChargeAnimSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetLogicTest {
    @Test
    fun chargeSlotIsFixedWhenTheUpdateIsMadeNotWhenItRuns() {
        var sheet: ChargeAnimSlot? = ChargeAnimSlot.PLUG_IN
        val update = ChargeAnimSlot.update(sheet!!, "import:x")
        sheet = null                                           // the sheet closes before DataStore runs the update
        val s = update(Settings())
        assertEquals("import:x", s.chargePlugInAnim)
        assertEquals("", s.chargeDoneAnim)
        assertEquals("import:y", ChargeAnimSlot.update(ChargeAnimSlot.DONE, "import:y")(Settings()).chargeDoneAnim)
    }

    @Test
    fun aSheetWaitsForItsItemToArriveBeforeItCanClose() {
        val sel = LiveSelection()
        // just added: not in the list yet, list loaded → keep waiting
        assertEquals("d:aa", sel.resolve("d:aa", present = false, loaded = true))
        // it arrives
        assertEquals("d:aa", sel.resolve("d:aa", present = true, loaded = true))
        // later it is removed elsewhere → close
        assertNull(sel.resolve("d:aa", present = false, loaded = true))
    }

    @Test
    fun anUnloadedListNeverClosesTheSheet() {
        val sel = LiveSelection()
        assertEquals("import:a", sel.resolve("import:a", present = true, loaded = true))
        // after rotation the flow starts empty: not loaded yet → keep the sheet
        val fresh = LiveSelection(seen = setOf("import:a"))
        assertEquals("import:a", fresh.resolve("import:a", present = false, loaded = false))
        assertNull(fresh.resolve("import:a", present = false, loaded = true))
    }

    @Test
    fun countsReadNaturally() {
        assertEquals("1 DRAWING", plural(1, "DRAWING"))
        assertEquals("3 DRAWINGS", plural(3, "DRAWING"))
        assertEquals("0 DRAWINGS", plural(0, "DRAWING"))
    }
}
