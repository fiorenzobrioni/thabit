package com.callbackdev.thabit.ui.wizard

import androidx.annotation.StringRes
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import com.callbackdev.thabit.ui.format.CodeFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** The four `when:` schemes, as the transcript offers them. */
enum class ScheduleScheme { Daily, Weekdays, Quota, Interval }

/**
 * The test being written, as a value.
 *
 * `$ thabit add` is a conversation, and this is what the conversation has
 * established so far. Every transition is a pure function of the draft, so the
 * whole wizard — defaults, cycles, validation, the habit that comes out the
 * other end — is asserted on the JVM without composing anything.
 *
 * **Everything except the name has a default** (VISION §4.5). Six mandatory
 * prompts would be eighteen answers in the sixty seconds §9 promises, so after
 * the name `[done]` adds the test as it stands and `[more]` walks the rest.
 */
data class WizardDraft(
    /** The habit being edited, or null when this is a new test. */
    val editing: Long? = null,
    val name: String = "",
    val type: HabitType = HabitType.BOOLEAN,
    /** Counter only: what is being counted — `pages`, `reps`, `glasses`. */
    val unit: String = DEFAULT_UNIT,
    /** Counter only: how much of it counts as done. */
    val target: Double = DEFAULT_TARGET,
    val scheme: ScheduleScheme = ScheduleScheme.Daily,
    val weekdays: Set<DayOfWeek> = DEFAULT_WEEKDAYS,
    val quota: Int = DEFAULT_QUOTA,
    val intervalDays: Int = DEFAULT_INTERVAL,
    val emoji: String? = null,
    /** The per-test reminder, or null for none — which is the default. */
    val remindAt: LocalTime? = null,
    /** True once `[more]` has been tapped — or always, when editing. */
    val expanded: Boolean = false
) {
    val isEditing: Boolean get() = editing != null

    /** The schedule the current scheme and its parameters spell out. */
    val schedule: Schedule
        get() = when (scheme) {
            ScheduleScheme.Daily -> Schedule.Daily
            ScheduleScheme.Weekdays -> Schedule.Weekdays(weekdays.ifEmpty { DEFAULT_WEEKDAYS })
            ScheduleScheme.Quota -> Schedule.Quota(quota)
            ScheduleScheme.Interval -> Schedule.Interval(intervalDays)
        }

    /** How each of the four `when:` tokens reads right now. */
    fun scheduleToken(scheme: ScheduleScheme): String = when (scheme) {
        ScheduleScheme.Daily -> Schedule.Daily.format()
        ScheduleScheme.Weekdays -> Schedule.Weekdays(weekdays.ifEmpty { DEFAULT_WEEKDAYS }).format()
        ScheduleScheme.Quota -> Schedule.Quota(quota).format()
        ScheduleScheme.Interval -> Schedule.Interval(intervalDays).format()
    }

    /** `pages >= 20` — the assertion, written the way the file writes it. */
    val assertText: String
        get() = "${unit.ifBlank { DEFAULT_UNIT }} >= ${CodeFormat.number(target)}"

    // ---- transitions -----------------------------------------------------

    fun withName(value: String) = copy(name = value)

    fun withType(value: HabitType) = copy(type = value)

    fun withUnit(value: String) = copy(unit = value.trim())

    /** An unreadable or non-positive answer leaves the target alone. */
    fun withTarget(text: String): WizardDraft {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return this
        return if (value > 0) copy(target = value) else this
    }

    fun withScheme(value: ScheduleScheme) = copy(scheme = value)

    /**
     * Toggles a weekday, refusing to leave the set empty.
     *
     * A weekday schedule with no days would be a test that is never due — a
     * quiet way of archiving something the user only meant to un-tick.
     */
    fun toggleWeekday(day: DayOfWeek): WizardDraft {
        val next = if (day in weekdays) weekdays - day else weekdays + day
        return if (next.isEmpty()) this else copy(weekdays = next)
    }

    fun cycleQuota() = copy(quota = if (quota >= MAX_QUOTA) 1 else quota + 1)

    fun cycleInterval(): WizardDraft {
        val index = INTERVAL_CYCLE.indexOf(intervalDays)
        val next = if (index >= 0) {
            INTERVAL_CYCLE[(index + 1) % INTERVAL_CYCLE.size]
        } else {
            // A value from an older test that is not one of the stops keeps its
            // place and moves on to the next one up, never snaps backwards.
            INTERVAL_CYCLE.firstOrNull { it > intervalDays } ?: INTERVAL_CYCLE.first()
        }
        return copy(intervalDays = next)
    }

    fun withEmoji(value: String?) = copy(emoji = value?.trim()?.takeIf { it.isNotEmpty() })

    /** `07:00`, or null for `[off]` — an empty answer is how a reminder is removed. */
    fun withRemind(value: LocalTime?) = copy(remindAt = value)

    fun expand() = copy(expanded = true)

    // ---- what comes out --------------------------------------------------

    /**
     * Why the test cannot be added yet, or null.
     *
     * A string id, not the words (Fase 15): `ERROR:` is a level and the renderer
     * keeps it, but what follows is a sentence telling the reader what to fix,
     * and a sentence is prose. This value stays pure — it names the message, the
     * screen speaks it — which is the same split the row comments use for their
     * spoken half.
     */
    @StringRes
    fun validationError(): Int? = when {
        name.isBlank() -> R.string.wiz_err_name
        type == HabitType.COUNTER && target <= 0 -> R.string.wiz_err_target
        type == HabitType.COUNTER && unit.isBlank() -> R.string.wiz_err_unit
        else -> null
    }

    val isValid: Boolean get() = validationError() == null

    private val assertSpec: AssertSpec?
        get() = if (type == HabitType.COUNTER) {
            // The step is always one, and the wizard never asks for it: `[+1]`
            // then appears exactly when a target is within a dozen taps (three
            // glasses of water, yes; twenty pages, no). Deriving a "set size"
            // from the target would put a number in the file that nobody typed.
            AssertSpec(target = target, unit = unit.ifBlank { DEFAULT_UNIT }, step = 1.0)
        } else {
            null
        }

    /** The new test, ready for the end of the file. */
    fun toHabit(createdAt: LocalDate, position: Int) = Habit(
        name = name.trim(),
        type = type,
        assert = assertSpec,
        schedule = schedule,
        remindAt = remindAt,
        emoji = emoji,
        position = position,
        createdAt = createdAt
    )

    /**
     * The edited test.
     *
     * `createdAt`, `position` and `archivedAt` are the habit's own history and
     * are never touched by an edit: a test does not become younger because its
     * name changed.
     */
    fun applyTo(habit: Habit) = habit.copy(
        name = name.trim(),
        type = type,
        assert = assertSpec,
        schedule = schedule,
        remindAt = remindAt,
        emoji = emoji
    )

    companion object {
        const val DEFAULT_UNIT = "times"
        const val DEFAULT_TARGET = 10.0
        const val DEFAULT_QUOTA = 3
        const val DEFAULT_INTERVAL = 2
        const val MAX_QUOTA = 7

        val DEFAULT_WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )

        /** The interval stops, from every other day to once a month. */
        val INTERVAL_CYCLE: List<Int> = listOf(2, 3, 4, 5, 7, 10, 14, 21, 30)

        /**
         * A time of day, read the forgiving way a terminal reads one.
         *
         * `7`, `7:30`, `07:30`, `730`, `0730` and `7.30` are all the same answer,
         * because a prompt that rejects `7` for a missing colon is a form
         * wearing a terminal's clothes. What it will not do is guess: anything
         * it cannot read comes back null, and the transcript says so in its own
         * compiler voice rather than silently storing a time nobody meant.
         *
         * A cycle was considered here and rejected, unlike everywhere else in
         * the app — `day_ends` has six sensible stops, but a wake-up time is any
         * of 1,440 and belongs to the person setting it. This is the one place
         * where the keyboard is the *smaller* interface.
         */
        fun parseTime(raw: String): LocalTime? {
            val text = raw.trim().replace('.', ':').replace(',', ':').replace('h', ':')
            if (text.isEmpty()) return null
            val hourText: String
            val minuteText: String
            if (':' in text) {
                val parts = text.split(':')
                if (parts.size != 2) return null
                hourText = parts[0]
                minuteText = parts[1].ifEmpty { "0" }
            } else {
                when (text.length) {
                    1, 2 -> { hourText = text; minuteText = "0" }
                    3 -> { hourText = text.take(1); minuteText = text.drop(1) }
                    4 -> { hourText = text.take(2); minuteText = text.drop(2) }
                    else -> return null
                }
            }
            val hour = hourText.toIntOrNull() ?: return null
            val minute = minuteText.toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return LocalTime.of(hour, minute)
        }

        /** Reopens an existing test in the transcript, prefilled and unfolded. */
        fun of(habit: Habit): WizardDraft {
            val schedule = habit.schedule
            return WizardDraft(
                editing = habit.id,
                name = habit.name,
                type = habit.type,
                unit = habit.assert?.unit?.ifBlank { DEFAULT_UNIT } ?: DEFAULT_UNIT,
                target = habit.assert?.target ?: DEFAULT_TARGET,
                scheme = when (schedule) {
                    is Schedule.Daily -> ScheduleScheme.Daily
                    is Schedule.Weekdays -> ScheduleScheme.Weekdays
                    is Schedule.Quota -> ScheduleScheme.Quota
                    is Schedule.Interval -> ScheduleScheme.Interval
                },
                weekdays = (schedule as? Schedule.Weekdays)?.days ?: DEFAULT_WEEKDAYS,
                quota = (schedule as? Schedule.Quota)?.times ?: DEFAULT_QUOTA,
                intervalDays = (schedule as? Schedule.Interval)?.everyDays ?: DEFAULT_INTERVAL,
                emoji = habit.emoji,
                remindAt = habit.remindAt,
                // An edit is never a two-question conversation: the reader came
                // here to change one specific thing and needs to see all of it.
                expanded = true
            )
        }
    }
}
