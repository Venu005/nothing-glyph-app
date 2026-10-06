package app.backlit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PolicyTextTest {
    @Test
    fun hardWrappedMarkdownBecomesCleanParagraphs() {
        val md = "# Title\n\n_Last updated: 2026-10-06_\n\nIntro line one\ncontinues here.\n\n- **Location:** first line\n  wraps here.\n- **Mic:** one line.\n\nContact: me@example.com\n"
        assertEquals(
            listOf("Last updated: 2026-10-06", "Intro line one continues here.", "• Location: first line wraps here.", "• Mic: one line.", "Contact: me@example.com"),
            PolicyText.paragraphs(md),
        )
    }

    @Test
    fun theRealPolicyHasNoMarkdownLeft() {
        val ps = PolicyText.paragraphs(File("../docs/privacy-policy.md").readText())
        assertTrue(ps.size > 5)
        for (p in ps) {
            assertFalse(p, p.contains("**") || p.startsWith("_") || p.startsWith("#") || p.contains("\n"))
        }
    }
}
