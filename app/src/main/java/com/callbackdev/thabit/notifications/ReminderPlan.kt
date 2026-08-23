package com.callbackdev.thabit.notifications

import com.callbackdev.thabit.domain.model.Habit
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When a reminder should next wake the app — the arithmetic, with no Android in
 * it.
 *
 * Two decisions live here and both are worth stating.
 *
 * **Reminders are wall-clock, not logical-day.** `remind: "07:00"` means seven in
 * the morning on the days the schedule names, whatever `day_ends` is set to. The
 * alternative — shifting the alarm by the logical boundary — would move a
 * reminder the user set by the clock on their wall, which is the one thing a
 * time-of-day setting may not do. The logical day comes back in at the other
 * end: the receiver asks the repository which day is open and looks the test up
 * *there*, so a nudge at 01:00 with `day_ends: "03:00"` is about the day that is
 * still running, exactly as the night owl means it.
 *
 * **The alarm is a wake-up, never a decision.** This plan only asks the calendar
 * question — does the schedule put the test on that date at all. Whether the
 * test is already passed, skipped, or a quota whose week is already met is a
 * fact about check rows at fire time, and it is re-read then (see
 * [ReminderReceiver]). That is what lets an archived test, an edited schedule or
 * a day already finished cost a silent wake-up instead of a wrong notification.
 */
object ReminderPlan {

    /** A year of calendar is enough for every schedule the MVP can spell. */
    private const val HORIZON_DAYS = 366

    /**
     * The next moment [habit]'s reminder should fire after [from], or null when
     * it has none, has left the suite, or its schedule never comes round again.
     */
    fun nextFire(habit: Habit, from: ZonedDateTime): ZonedDateTime? {
        val time = habit.remindAt ?: return null
        if (habit.archivedAt != null) return null

        var date = from.toLocalDate()
        repeat(HORIZON_DAYS + 1) {
            // `of` resolves a DST gap forward instead of throwing: on the night
            // the clocks jump, a 02:30 reminder rings at 03:30 rather than being
            // lost. A nudge that skips a day twice a year is a bug nobody would
            // ever manage to report.
            val candidate = ZonedDateTime.of(date, time, from.zone)
            if (candidate.isAfter(from) && habit.occursOn(date)) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    /**
     * The next evening digest after [from].
     *
     * Every day, unconditionally: whether it has anything to say is decided when
     * it fires, because what is still pending at eight in the evening is not
     * knowable at eight in the morning.
     */
    fun nextDigest(hour: LocalTime, from: ZonedDateTime): ZonedDateTime {
        val today = ZonedDateTime.of(from.toLocalDate(), hour, from.zone)
        return if (today.isAfter(from)) today else ZonedDateTime.of(
            from.toLocalDate().plusDays(1),
            hour,
            from.zone
        )
    }
}
