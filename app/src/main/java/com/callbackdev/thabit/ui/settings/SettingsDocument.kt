package com.callbackdev.thabit.ui.settings

import androidx.annotation.StringRes
import com.callbackdev.thabit.R
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
 * The register rule (VISION §1.3) splits the file in two, and the split runs
 * *inside* the comment channel rather than around it. The **file** is code and
 * stays English: keys, values, `// active`, the opacity list, `// Last modified:`
 * and the `$` commands. The **notes** are sentences that exist only to be
 * understood, so they are string ids here and words in the reader's language at
 * render time. The timestamp behind `// Last modified:` was already localized
 * for the same reason one layer down: it is a data value.
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
    val widgetOpacityPct: Int = 100
) {
    val digestHourValue: String get() = CodeFormat.time(notifications.digestHour)

    /** The next value a tap on `digest_hour` moves to. */
    fun cycledDigestHour(): LocalTime = nextDigestHour(notifications.digestHour)

    /** The next value a tap on `bg_opacity_pct` moves to. */
    fun cycledWidgetOpacity(): Int = nextWidgetOpacity(widgetOpacityPct)

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

        /**
         * **Hints are the file's own annotations and stay English** — a marker
         * and a list of the values a tap walks through. Nothing here is a
         * sentence: `// active` is the same word `cities.json` uses next door in
         * tweather, and the opacity list is the values themselves.
         */
        const val ACTIVE_HINT: String = "// active"
        val WIDGET_OPACITY_HINT: String = "// ${WidgetOpacities.joinToString(" | ")}"

        /**
         * **Notes are sentences addressed to the reader, so they are resources**
         * (Fase 15). Each one explains what a setting *means*, which is the one
         * job a comment can have that the reader has to understand to do
         * anything with the line above it.
         *
         * The document still decides which note each key carries — that is the
         * whole point of building the file as a value — but it now says so with
         * a string id instead of the words, and the renderer speaks them in the
         * reader's language. It is the same split the row comments already use
         * for the spoken half: **the document carries the structure, the
         * renderer carries the language.**
         *
         * The `// ` marker is not part of the resource. The syntax is the
         * fiction and does not translate; only the sentence after it does.
         */
        @StringRes val DAY_ENDS_NOTE: Int = R.string.cfg_day_ends

        @StringRes val WEEK_STARTS_NOTE: Int = R.string.cfg_week_starts

        @StringRes val DAILY_COMMIT_NOTE: Int = R.string.cfg_daily_commit

        @StringRes val PENDING_DIGEST_NOTE: Int = R.string.cfg_pending_digest

        @StringRes val DIGEST_HOUR_NOTE: Int = R.string.cfg_digest_hour

        @StringRes val REMINDERS_NOTE: Int = R.string.cfg_reminders

        /**
         * The answer to an export with an empty database.
         *
         * It used to say the writer had not arrived yet; now it says the only
         * other honest thing — there is nothing in there. An export that wrote
         * an empty file would be worse than one that says so.
         */
        @StringRes val EXPORT_PENDING_NOTE: Int = R.string.cfg_export_pending

        /** Shared with `habits.test`: one sentence, so one string. */
        @StringRes val CONFIRM_NOTE: Int = R.string.confirm_command

        /**
         * `$ git restore settings.config` puts the config back and touches
         * nothing else — stated in the file, next to the command, because a
         * destructive-looking verb should say what it will not destroy.
         */
        @StringRes val RESTORE_NOTE: Int = R.string.cfg_restore_hint

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
