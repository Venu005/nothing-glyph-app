package app.backlit.alerts

import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlertsChargeHooksTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun importedAnimationIgnoresNonImports() {
        val lib = AnimationLibrary(tmp.root)
        assertNull(lib.importedOnly("builtin:heart", emptyList()))
        assertNull(lib.importedOnly("", emptyList()))
        assertNull(lib.importedOnly("import:gone", listOf(AnimIndexEntry("import:gone", "Gone", 1))))   // file missing
    }
}
