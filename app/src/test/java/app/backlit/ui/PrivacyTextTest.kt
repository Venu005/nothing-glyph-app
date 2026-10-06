package app.backlit.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PrivacyTextTest {
    @Test
    fun bundledPrivacyPolicyMatchesTheDocs() {
        assertEquals(File("../docs/privacy-policy.md").readText(), File("src/main/res/raw/privacy_policy.txt").readText())
    }
}
