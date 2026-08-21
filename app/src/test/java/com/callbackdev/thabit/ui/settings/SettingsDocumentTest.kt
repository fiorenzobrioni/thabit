package com.callbackdev.thabit.ui.settings

import com.callbackdev.thabit.ui.theme.ThemeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * `settings.config` as a value: the words in the file, and where each control
 * goes next when it is tapped.
 */
class SettingsDocumentTest {

    private fun doc(
        dayEnds: LocalTime = LocalTime.MIDNIGHT,
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
        theme: ThemeProfile = ThemeProfile.Obsidian,
        showLineNumbers: Boolean = false,
        wordWrap: Boolean = false,
        lastModified: Long? = null
    ) = SettingsDocument(
        dayEnds = dayEnds,
        weekStartsOn = weekStartsOn,
        theme = theme,
        showLineNumbers = showLineNumbers,
        wordWrap = wordWrap,
        lastModified = lastModified,
        versionName = "0.1.0"
    )

    @Test
    fun `the values are written the way the file writes them`() {
        val document = doc(dayEnds = LocalTime.of(3, 0), weekStartsOn = DayOfWeek.SUNDAY)
        assertEquals("03:00", document.dayEndsValue)
        assertEquals("sunday", document.weekStartsValue)
        assertEquals("obsidian", document.activeProfileValue)
    }

    @Test
    fun `day_ends cycles through midnight and the small hours, and wraps`() {
        assertEquals(LocalTime.of(1, 0), doc(dayEnds = LocalTime.MIDNIGHT).cycledDayEnds())
        assertEquals(LocalTime.of(4, 0), doc(dayEnds = LocalTime.of(3, 0)).cycledDayEnds())
        assertEquals(LocalTime.MIDNIGHT, doc(dayEnds = LocalTime.of(5, 0)).cycledDayEnds())
    }

    @Test
    fun `a day_ends outside the cycle moves forward instead of snapping back`() {
        // A value that arrived from an export or a later version keeps its place:
        // the next tap goes to the stop after it, never silently rewrites it.
        assertEquals(LocalTime.of(4, 0), doc(dayEnds = LocalTime.of(3, 30)).cycledDayEnds())
        assertEquals(LocalTime.MIDNIGHT, doc(dayEnds = LocalTime.of(23, 0)).cycledDayEnds())
    }

    @Test
    fun `week_starts cycles through the three that people actually use`() {
        assertEquals(DayOfWeek.SUNDAY, doc(weekStartsOn = DayOfWeek.MONDAY).cycledWeekStart())
        assertEquals(DayOfWeek.SATURDAY, doc(weekStartsOn = DayOfWeek.SUNDAY).cycledWeekStart())
        assertEquals(DayOfWeek.MONDAY, doc(weekStartsOn = DayOfWeek.SATURDAY).cycledWeekStart())
    }

    @Test
    fun `the profile list marks exactly one as active`() {
        val profiles = doc(theme = ThemeProfile.Dracula).profiles
        assertEquals(listOf("obsidian", "dracula", "monokai"), profiles.map { it.value })
        assertEquals(1, profiles.count { it.active })
        assertTrue(profiles.single { it.active }.profile == ThemeProfile.Dracula)
    }

    @Test
    fun `an untouched file has no last-modified stamp to show`() {
        assertNull(doc().lastModified)
        assertEquals(1_787_000_000_000L, doc(lastModified = 1_787_000_000_000L).lastModified)
    }

    @Test
    fun `the sections that are not wired yet say so instead of showing dead switches`() {
        assertFalse(doc().notificationsWired)
        assertFalse(doc().exportWired)
        assertTrue(SettingsDocument.NOTIFICATIONS_PLACEHOLDER.startsWith("//"))
    }

    @Test
    fun `the reset says out loud what it will not touch`() {
        assertTrue(SettingsDocument.RESTORE_HINT.contains("suite"))
        assertTrue(SettingsDocument.RESTORE_HINT.contains("history"))
    }

    @Test
    fun `the hints are source, so they are English and marked as comments`() {
        listOf(
            SettingsDocument.DAY_ENDS_HINT,
            SettingsDocument.WEEK_STARTS_HINT,
            SettingsDocument.ACTIVE_HINT,
            SettingsDocument.NOTIFICATIONS_PLACEHOLDER,
            SettingsDocument.RESTORE_HINT
        ).forEach { assertTrue("'$it' is not a comment", it.startsWith("//")) }
    }
}
