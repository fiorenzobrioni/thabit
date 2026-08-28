package com.callbackdev.thabit.ui.settings

import com.callbackdev.thabit.R
import com.callbackdev.thabit.data.NotificationSettings
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
        lastModified: Long? = null,
        notifications: NotificationSettings = NotificationSettings(),
        reminderCount: Int = 0,
        widgetOpacityPct: Int = 100
    ) = SettingsDocument(
        dayEnds = dayEnds,
        weekStartsOn = weekStartsOn,
        theme = theme,
        showLineNumbers = showLineNumbers,
        wordWrap = wordWrap,
        lastModified = lastModified,
        versionName = "0.1.0",
        notifications = notifications,
        reminderCount = reminderCount,
        widgetOpacityPct = widgetOpacityPct
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
    fun `every section of the file is wired now, and the empty-export line says why`() {
        // The `notificationsWired`/`exportWired` flags are gone with the phases
        // that needed them: a flag whose answer is always yes is dead code, and
        // the constant it guarded now describes an empty database instead of a
        // missing feature.
        assertEquals(R.string.cfg_export_pending, SettingsDocument.EXPORT_PENDING_NOTE)
    }

    @Test
    fun `the notifications block knows about the reminders it does not own`() {
        // Per-test reminders live on the test, so the block would otherwise
        // imply that two `false`s mean silence. The count is what this document
        // owns; the sentence it turns into is asserted, in both languages, in
        // SettingsScreenTest.
        val silent = NotificationSettings(dailyCommit = false, pendingDigest = false)
        assertEquals(0, doc(notifications = silent).reminderCount)
        assertFalse(doc(notifications = silent).anyNotification)
        assertTrue(doc(notifications = silent, reminderCount = 1).anyNotification)
    }

    @Test
    fun `something can post when a switch is on, or when a test carries a reminder`() {
        assertFalse(doc(notifications = NotificationSettings(dailyCommit = false)).anyNotification)
        assertTrue(doc(notifications = NotificationSettings(dailyCommit = true)).anyNotification)
        assertTrue(doc(notifications = NotificationSettings(pendingDigest = true, dailyCommit = false)).anyNotification)
        // The case the file would otherwise get wrong.
        assertTrue(
            doc(notifications = NotificationSettings(dailyCommit = false), reminderCount = 1)
                .anyNotification
        )
    }

    @Test
    fun `digest_hour cycles through the evening and wraps`() {
        assertEquals(
            LocalTime.of(21, 0),
            doc(notifications = NotificationSettings(digestHour = LocalTime.of(20, 0)))
                .cycledDigestHour()
        )
        assertEquals(
            LocalTime.of(18, 0),
            doc(notifications = NotificationSettings(digestHour = LocalTime.of(22, 0)))
                .cycledDigestHour()
        )
        // A value from outside the cycle keeps its place instead of snapping back.
        assertEquals(LocalTime.of(20, 0), SettingsDocument.nextDigestHour(LocalTime.of(19, 30)))
    }

    @Test
    fun `the widget opacity cycles down the values the hint lists`() {
        assertEquals(85, doc(widgetOpacityPct = 100).cycledWidgetOpacity())
        assertEquals(50, doc(widgetOpacityPct = 70).cycledWidgetOpacity())
        // Wraps back to the top, like every other cycle in the file.
        assertEquals(100, doc(widgetOpacityPct = 50).cycledWidgetOpacity())
        // A value from outside the cycle steps to the next one DOWN, because
        // this is the one cycle whose list descends.
        assertEquals(70, SettingsDocument.nextWidgetOpacity(80))
        assertTrue(SettingsDocument.WIDGET_OPACITY_HINT.startsWith("// 100 | 85"))
    }

    /**
     * The register rule as a test (Fase 15). It replaces the one that asserted
     * every hint was an English literal starting with `//`, which is exactly the
     * belief the rule corrected: the marker is not the register.
     */
    @Test
    fun `the notes are resources, and every key carries a different one`() {
        val notes = listOf(
            SettingsDocument.DAY_ENDS_NOTE,
            SettingsDocument.WEEK_STARTS_NOTE,
            SettingsDocument.DAILY_COMMIT_NOTE,
            SettingsDocument.PENDING_DIGEST_NOTE,
            SettingsDocument.DIGEST_HOUR_NOTE,
            SettingsDocument.REMINDERS_NOTE,
            SettingsDocument.EXPORT_PENDING_NOTE,
            SettingsDocument.CONFIRM_NOTE,
            SettingsDocument.RESTORE_NOTE
        )
        notes.forEach { assertTrue("a note with no resource behind it", it != 0) }
        assertEquals("two keys share a note", notes.size, notes.toSet().size)
    }

    /**
     * What is *not* prose stays a literal, and stays English: a one-word marker
     * and the list of values a tap walks through. Both carry the `//` here,
     * because for these two the marker and the words really are one thing.
     */
    @Test
    fun `the hints that are markers are still English literals`() {
        assertEquals("// active", SettingsDocument.ACTIVE_HINT)
        assertTrue(SettingsDocument.WIDGET_OPACITY_HINT.startsWith("// 100 | 85"))
    }
}
