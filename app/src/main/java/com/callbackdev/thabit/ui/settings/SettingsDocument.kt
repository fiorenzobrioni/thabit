package com.callbackdev.thabit.ui.settings

import com.callbackdev.thabit.data.NotificationSettings
import com.callbackdev.thabit.data.WidgetOpacities
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.theme.ThemeProfile
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

/**
 * `settings.config` as a document — the series' JSON-with-comments, worked out
 * as a value.
 *
 * Same split as `habits.test` (Fase 3): the file's exact words are a pure value
 * so they can be asserted character by character, and the screen does nothing but
 * draw them and hand taps back.
 *
 * Everything here is the **code channel** and therefore English: keys, values and
 * hints are source. The one localized thing in the file is the timestamp behind
 * `// Last modified:`, which is a data value and gets formatted by the renderer
 * in the reader's language (series rule, VISION §1.3).
 */
data class SettingsDocument(
    val dayEnds: LocalTime,
    val weekStartsOn: DayOfWeek,
    val theme: ThemeProfile,
    val showLineNumbers: Boolean,
    val wordWrap: Boolean,
    /** Epoch millis, or null while the file is still exactly what shipped. */
    val lastModified: Long?,
    val versionName: String,
    val notifications: NotificationSettings = NotificationSettings(),
    /**
     * How many live tests carry a reminder.
     *
     * Not a setting and not editable here — it is the `notifications` block
     * telling the truth about itself. Per-test reminders live on the test
     * (VISION §4.4), so this file would otherwise claim that turning everything
     * here off means nothing will ever post, which is false the moment one test
     * has an alarm. Stating the count and where it is set is how the block stays
     * honest without becoming a second place to edit it.
     */
    val reminderCount: Int = 0,
    /** Home-widget background opacity, as a percentage. */
    val widgetOpacityPct: Int = 100,
    /** False until Fase 11: the export commands answer honestly meanwhile. */
    val exportWired: Boolean = EXPORT_SHIPPED
) {
    val digestHourValue: String get() = CodeFormat.time(notifications.digestHour)

    /** The next value a tap on `digest_hour` moves to. */
    fun cycledDigestHour(): LocalTime = nextDigestHour(notifications.digestHour)

    /** The next value a tap on `bg_opacity_pct` moves to. */
    fun cycledWidgetOpacity(): Int = nextWidgetOpacity(widgetOpacityPct)

    /** `// 2 tests carry a reminder — set on the test, in habits.test`. */
    val remindersComment: String
        get() = if (reminderCount == 0) {
            "// no test carries a reminder yet — set one from a test's [edit]"
        } else {
            val noun = if (reminderCount == 1) "test carries" else "tests carry"
            "// $reminderCount $noun a reminder — set on the test, in habits.test"
        }

    /** True when something in this app could actually post. */
    val anyNotification: Boolean
        get() = notifications.dailyCommit || notifications.pendingDigest || reminderCount > 0

    val dayEndsValue: String get() = CodeFormat.time(dayEnds)

    val weekStartsValue: String get() = weekStartsOn.name.lowercase(Locale.ROOT)

    val activeProfileValue: String get() = theme.name.lowercase(Locale.ROOT)

    /** The three profiles, in a fixed order, with the active one marked. */
    val profiles: List<ProfileEntry>
        get() = ThemeProfile.entries.map { profile ->
            ProfileEntry(profile, profile.name.lowercase(Locale.ROOT), profile == theme)
        }

    /** The next value a tap on `day_ends` moves to. */
    fun cycledDayEnds(): LocalTime = nextDayEnds(dayEnds)

    /** The next value a tap on `week_starts` moves to. */
    fun cycledWeekStart(): DayOfWeek = nextWeekStart(weekStartsOn)

    data class ProfileEntry(val profile: ThemeProfile, val value: String, val active: Boolean)

    companion object {
        const val FILE_NAME: String = "settings.config"

        /** Wired in Fase 11. */
        const val EXPORT_SHIPPED: Boolean = false

        /**
         * The stops `day_ends` cycles through: midnight and the small hours.
         *
         * A cycle instead of a free prompt, and that is a decision rather than a
         * shortcut — `day_ends` exists for people whose day ends at two in the
         * morning (VISION §6.3), and every one of those answers is a whole hour.
         * A cycle keeps the keyboard out of the settings file entirely, which is
         * how the whole series' config behaves. An exact `03:30` is a case
         * nobody has asked for; when somebody does, the prompt is one line away.
         */
        val DAY_ENDS_CYCLE: List<LocalTime> = listOf(
            LocalTime.MIDNIGHT,
            LocalTime.of(1, 0),
            LocalTime.of(2, 0),
            LocalTime.of(3, 0),
            LocalTime.of(4, 0),
            LocalTime.of(5, 0)
        )

        /** Where the display grids start. ISO weeks are unaffected — see [com.callbackdev.thabit.domain.IsoWeek]. */
        val WEEK_START_CYCLE: List<DayOfWeek> =
            listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)

        /**
         * The stops `digest_hour` cycles through — the evening, hour by hour.
         *
         * A cycle for the same reason `day_ends` is one: no keyboard in the
         * config file. The range is deliberately narrow because the setting is
         * narrow — this is the hour of an **evening** summary of what is still
         * open, and a digest at nine in the morning would be a to-do list
         * notification, which is the thing VISION §3.3.4 refuses to build.
         */
        val DIGEST_HOUR_CYCLE: List<LocalTime> = listOf(
            LocalTime.of(18, 0),
            LocalTime.of(19, 0),
            LocalTime.of(20, 0),
            LocalTime.of(21, 0),
            LocalTime.of(22, 0)
        )

        // Hints are source, so they stay English (VISION §1.3).
        const val DAY_ENDS_HINT: String = "// the nightly build; \"03:00\" if your day ends late"
        const val WEEK_STARTS_HINT: String = "// where the heatmap and the week table start"
        const val ACTIVE_HINT: String = "// active"
        const val DAILY_COMMIT_HINT: String = "// the day's build result, silent, at commit"
        const val PENDING_DIGEST_HINT: String = "// one evening summary — never one nag per test"
        const val DIGEST_HOUR_HINT: String = "// when that summary goes out"
        const val REMINDERS_HINT: String =
            "// reminders are approximate — a nudge, not an alarm clock"
        val WIDGET_OPACITY_HINT: String = "// ${WidgetOpacities.joinToString(" | ")}"
        const val EXPORT_PENDING: String =
            "// nothing to export yet — the writer arrives with its own phase"
        const val RESTORE_CONFIRM: String = "// tap the command to confirm"

        /**
         * `$ git restore settings.config` puts the config back and touches
         * nothing else — stated in the file, next to the command, because a
         * destructive-looking verb should say what it will not destroy.
         */
        const val RESTORE_HINT: String = "// resets this file only — the suite and its history are untouched"

        /**
         * A value not in the cycle keeps its place: the next tap moves on to the
         * stop after it rather than snapping backwards, so a setting arriving
         * from an export or a future version is never silently rewritten.
         */
        /** The cycles as pure functions, so a control never needs a rendered file. */
        fun nextDayEnds(current: LocalTime): LocalTime = DAY_ENDS_CYCLE.nextAfter(current)

        fun nextWeekStart(current: DayOfWeek): DayOfWeek = WEEK_START_CYCLE.nextAfter(current)

        fun nextDigestHour(current: LocalTime): LocalTime = DIGEST_HOUR_CYCLE.nextAfter(current)

        /**
         * Descending, unlike every other cycle in the file, because the values
         * are: the list reads `100 | 85 | 70 | 50` and a tap walks it in the
         * order it is written. `nextAfter` would jump backwards through it.
         */
        fun nextWidgetOpacity(current: Int): Int {
            val index = WidgetOpacities.indexOf(current)
            if (index >= 0) return WidgetOpacities[(index + 1) % WidgetOpacities.size]
            return WidgetOpacities.firstOrNull { it < current } ?: WidgetOpacities.first()
        }

        private fun <T : Comparable<T>> List<T>.nextAfter(current: T): T {
            val index = indexOf(current)
            if (index >= 0) return this[(index + 1) % size]
            return firstOrNull { it > current } ?: first()
        }

        fun of(
            dayEnds: LocalTime,
            weekStartsOn: DayOfWeek,
            theme: ThemeProfile,
            showLineNumbers: Boolean,
            wordWrap: Boolean,
            lastModified: Long?,
            versionName: String,
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
            versionName = versionName,
            notifications = notifications,
            reminderCount = reminderCount,
            widgetOpacityPct = widgetOpacityPct
        )
    }
}
