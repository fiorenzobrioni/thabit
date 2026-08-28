package com.callbackdev.thabit.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.R
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The register rule as a test (`PLANNING.md` Fase 15), read off the resources
 * themselves rather than off any one screen.
 *
 * The per-screen tests check that a line is drawn; this checks the thing the rule
 * is actually about — that what moved is prose and what stayed is code — and it
 * checks it for **every** string at once.
 *
 * thabit is swept whole rather than by a `note_*` prefix, because its prose does
 * not carry one: it is spread over `suite_`, `log_`, `cfg_`, `stats_`, `note_`,
 * `readme_`, `wiz_` and the hundred-odd `cd_` accessibility strings, and a guard
 * that only looked at one of those would be the half-kept rule this phase was
 * written against. Anything genuinely English on both sides is `translatable`
 * `="false"` or named in [identicalOnPurpose], with its reason.
 *
 * Backported from tsteps' Fase 20 (`../tsteps`), which learned it from tweather's
 * Fase 18 device round: thabit implemented the rule first, before either guard
 * existed, so it was the one app in the series where nothing was watching.
 */
@RunWith(RobolectricTestRunner::class)
class RegisterRuleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resources: Resources get() = context.resources

    /**
     * Every **translatable** string the app can print.
     *
     * `translatable="false"` lives in the XML and nothing at runtime can see it,
     * so the list is taken from the declaration itself rather than from
     * `R.string` alone: that attribute is where "this one is a brand name, not a
     * sentence" is already written down, and a test that ignored it would ask for
     * an Italian `thabit`.
     */
    private val strings: List<Pair<String, Int>> by lazy {
        val xml = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
            .firstOrNull { it.isFile }
            ?: error("cannot find values/strings.xml: this guard must not pass by default")
        val declared = Regex("""<string name="([a-z0-9_]+)"([^>]*)>""")
            .findAll(xml.readText())
            .filterNot { it.groupValues[2].contains("translatable=\"false\"") }
            .map { it.groupValues[1] }
            .toSet()
        R.string::class.java.declaredFields
            .filter { it.name in declared }
            .map { it.name to it.getInt(null) }
            .sortedBy { it.first }
    }

    @Test
    fun `the sweep found the strings at all`() {
        // A reflective list that silently came back empty would make every other
        // test in this class pass by vacuum.
        assertTrue("suspiciously few strings: ${strings.size}", strings.size >= 200)
    }

    /**
     * The marker is the host file's syntax and the renderer adds it — `#` inside
     * `habits.test`, `//` inside `settings.config` (VISION §1.1). A string that
     * carried its own could only ever be printed in one of the two files.
     */
    @Test
    fun `no string carries its own comment marker`() {
        strings.forEach { (name, id) ->
            val text = resources.getString(id)
            assertTrue("$name carries its own marker: '$text'",
                !text.startsWith("//") && !text.startsWith("# "))
        }
    }

    /**
     * And none carries a level either: `ERROR:` and `WARN:` are tokens of the
     * channel, and a translated `ERRORE:` would be the one word on the line a
     * reader looking for a log level cannot find.
     */
    @Test
    fun `no string carries its own level`() {
        strings.forEach { (name, id) ->
            val text = resources.getString(id)
            assertTrue("$name carries a level: '$text'",
                !text.startsWith("ERROR") && !text.startsWith("WARN"))
        }
    }

    /**
     * The strings that read the same in both languages, and why. Each one is a
     * decision; inventing a difference to satisfy a test would be writing worse
     * Italian to get a green tick.
     *
     * The three nav labels are the standing one: `Stats` never became
     * `Statistiche`, because the bar has four slots at 360dp and the words in it
     * are the app's own tabs. The rest are a format with no words in it, two
     * nouns Italian borrowed whole (`emoji`, `record`), and a comma.
     */
    private val identicalOnPurpose: Set<String> = setOf(
        "nav_editor", "nav_log", "nav_stats",
        "cd_detail_counter_done", "cd_wizard_emoji", "cd_log_tag",
        "readme_clause_separator"
    )

    /**
     * Everything else is actually translated. A rule kept two hundred times out of
     * two hundred and thirty does not read as a decision, it reads as a job
     * somebody abandoned halfway.
     */
    @Test
    @Config(qualifiers = "it")
    fun `every string says something different in Italian`() {
        val english = context.createConfigurationContext(
            Configuration(resources.configuration).apply { setLocale(Locale.ENGLISH) }
        ).resources
        val unchanged = strings
            .filter { (name, id) ->
                name !in identicalOnPurpose && resources.getString(id) == english.getString(id)
            }
            .map { it.first }
        assertEquals("these strings were never translated: $unchanged", emptyList<String>(), unchanged)
    }

    /** And the allowlist must not rot into a list of names nobody looked at. */
    @Test
    fun `every allowlisted name still exists`() {
        val known = strings.map { it.first }.toSet()
        identicalOnPurpose.forEach {
            assertTrue("'$it' is allowlisted but is no longer a string", it in known)
        }
    }

    /**
     * The tokens survive the translation. A key, a verdict, a `$` command or a
     * word `git` itself keeps is still the thing the reader has to look for, so it
     * comes through both languages unchanged.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the tokens inside a translated sentence survive it`() {
        // git translates "On branch main" in every language it ships, and never
        // translates `branch` or `commit`. That is the whole rule in one line.
        val branch = resources.getString(R.string.log_branch)
        assertTrue(branch, branch.contains("branch"))
        assertTrue(branch, !branch.contains("On branch"))
        assertTrue(resources.getString(R.string.cfg_export_wrote).contains("Downloads/"))
        assertTrue(resources.getString(R.string.suite_empty_readme).contains("README"))
    }
}
