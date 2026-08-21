package com.callbackdev.thabit.ui.settings

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
    /** False until Fase 9 wires them: the section says so instead of pretending. */
    val notificationsWired: Boolean = NOTIFICATIONS_SHIPPED,
    /** False until Fase 11: the export commands answer honestly meanwhile. */
    val exportWired: Boolean = EXPORT_SHIPPED
) {
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

        /** Wired in Fase 9. Until then the section refuses to show switches that do nothing. */
        const val NOTIFICATIONS_SHIPPED: Boolean = false

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

        // Hints are source, so they stay English (VISION §1.3).
        const val DAY_ENDS_HINT: String = "// the nightly build; \"03:00\" if your day ends late"
        const val WEEK_STARTS_HINT: String = "// where the heatmap and the week table start"
        const val ACTIVE_HINT: String = "// active"
        const val NOTIFICATIONS_PLACEHOLDER: String =
            "// not wired yet — reminders and the daily commit arrive with their own phase"
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
            versionName: String
        ) = SettingsDocument(
            dayEnds = dayEnds,
            weekStartsOn = weekStartsOn,
            theme = theme,
            showLineNumbers = showLineNumbers,
            wordWrap = wordWrap,
            lastModified = lastModified,
            versionName = versionName
        )
    }
}
