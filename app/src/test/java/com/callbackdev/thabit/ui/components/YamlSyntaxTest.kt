package com.callbackdev.thabit.ui.components

import androidx.compose.ui.graphics.Color
import com.callbackdev.thabit.ui.theme.ObsidianSyntax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The YAML builders are pure functions (state → colored AnnotatedString lines), so
 * the highlighting contract — thabit's checkbox tokens above all — is asserted here
 * without rendering anything. Modeled on JsonSyntaxTest.
 */
class YamlSyntaxTest {

    private val syntax = ObsidianSyntax

    /** Color of the span covering [sub] (first occurrence), or null if unspanned. */
    private fun CodeLine.colorOf(sub: String): Color? {
        val start = text.text.indexOf(sub)
        assertTrue("'$sub' not found in '${text.text}'", start >= 0)
        return text.spanStyles
            .firstOrNull { it.start <= start && start + sub.length <= it.end }
            ?.item?.color
    }

    @Test
    fun `test line renders dash checkbox name and trailing comment`() {
        val line = yamlTestLine(CheckboxState.Passed, "meditate 10 min", syntax, comment = "07:12")
        assertEquals("- [x] meditate 10 min  # 07:12", line.text.text)
    }

    @Test
    fun `checkbox states carry their token colors`() {
        assertEquals(
            syntax.diffAdd,
            yamlTestLine(CheckboxState.Passed, "a", syntax).colorOf("[x]")
        )
        assertEquals(
            syntax.comment,
            yamlTestLine(CheckboxState.Skipped, "a", syntax).colorOf("[~]")
        )
        assertEquals(
            syntax.diffDel,
            yamlTestLine(CheckboxState.Failed, "a", syntax).colorOf("[!]")
        )
        assertEquals(
            syntax.comment,
            yamlTestLine(CheckboxState.Holding, "a", syntax).colorOf("[·]")
        )
    }

    @Test
    fun `pending checkbox is neutral — no color span, inherits on-surface`() {
        val line = yamlTestLine(CheckboxState.Pending, "pushups", syntax)
        assertEquals(null, line.colorOf("[ ]"))
    }

    @Test
    fun `a test line carries the words a screen reader will say`() {
        // The builders are pure and hold no Context, so the caller composes the
        // sentence; the row only has to carry it (VISION §3.3.7).
        val line = yamlTestLine(
            CheckboxState.Holding, "no sugar", syntax,
            comment = "holds — asserts at commit",
            contentDescription = "no sugar, sta reggendo"
        )
        assertEquals("no sugar, sta reggendo", line.contentDescription)
        assertEquals(null, yamlTestLine(CheckboxState.Passed, "a", syntax).contentDescription)
    }

    @Test
    fun `every checkbox state points at its own spoken string`() {
        val ids = CheckboxState.entries.map { it.spokenRes }
        ids.forEach { assertTrue("a state has no spoken string resource", it != 0) }
        assertEquals(CheckboxState.entries.size, ids.distinct().size)
    }

    @Test
    fun `holding is a glyph of its own — an avoid test is not a pending one`() {
        // On the widget there is no comment channel to disambiguate, so the glyph
        // has to (VISION §4.1): `[·] no sugar` holds, `[ ] pushups` is still to do.
        assertEquals("[·]", CheckboxState.Holding.glyph)
        assertEquals(
            "- [·] no sugar  # holds — asserts at commit",
            yamlTestLine(
                CheckboxState.Holding, "no sugar", syntax,
                comment = "holds — asserts at commit"
            ).text.text
        )
    }

    @Test
    fun `test name is user data and stays unspanned`() {
        val line = yamlTestLine(CheckboxState.Passed, "read 20 pages 📖", syntax, comment = "23 pages")
        assertEquals(null, line.colorOf("read 20 pages 📖"))
    }

    @Test
    fun `dash is list punctuation and trailing comment is the dimmed hint channel`() {
        val line = yamlTestLine(CheckboxState.Pending, "pushups", syntax, comment = "12/30")
        assertEquals(syntax.comment, line.colorOf("- "))
        assertEquals(syntax.comment.copy(alpha = 0.6f), line.colorOf("# 12/30"))
    }

    @Test
    fun `emoji and glyphs inside names and comments do not break tokenization`() {
        val line = yamlTestLine(
            CheckboxState.Pending, "health check", syntax, comment = "▓▓▓▓▓▓▓▓░░ 82%"
        )
        assertEquals("- [ ] health check  # ▓▓▓▓▓▓▓▓░░ 82%", line.text.text)
        assertEquals(syntax.comment.copy(alpha = 0.6f), line.colorOf("▓▓"))
    }

    @Test
    fun `key value lines color key and scalar by type`() {
        val string = yamlStringLine("when", "daily", syntax)
        assertEquals("when: daily", string.text.text)
        assertEquals(syntax.key, string.colorOf("when"))
        assertEquals(syntax.string, string.colorOf("daily"))

        val number = yamlNumberLine("streak", "18", syntax, comment = "days")
        assertEquals("streak: 18  # days", number.text.text)
        assertEquals(syntax.number, number.colorOf("18"))
    }

    @Test
    fun `quoted string keeps its quotes inside the string color`() {
        val line = yamlStringLine("remind", "07:00", syntax, quoted = true)
        assertEquals("remind: \"07:00\"", line.text.text)
        assertEquals(syntax.string, line.colorOf("\"07:00\""))
    }

    @Test
    fun `indent click and label pass through to the CodeLine`() {
        var taps = 0
        val line = yamlTestLine(
            CheckboxState.Pending, "journal", syntax,
            indent = 1, onClick = { taps++ }, onClickLabel = "Pass journal"
        )
        assertEquals(1, line.indent)
        assertEquals("Pass journal", line.onClickLabel)
        line.onClick!!.invoke()
        assertEquals(1, taps)
    }

    @Test
    fun `bare checkbox token matches the test line rendering`() {
        assertEquals("[x]", checkboxToken(CheckboxState.Passed, syntax).text)
        assertEquals("[!]", checkboxToken(CheckboxState.Failed, syntax).text)
    }
}
