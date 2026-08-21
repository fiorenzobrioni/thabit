package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import java.time.LocalDate

/**
 * Everything the pure engines are allowed to know: the suite, the check rows and
 * the days that actually ran.
 *
 * It is a plain read model, indexed once and shared by every calculation below
 * it, so `Verdicts`, `Streaks`, `Health`, `Coverage`, `Regressions` and
 * `Records` never touch a database, a clock or a locale. That is what makes the
 * whole of Fase 2 testable on the JVM — and what makes every number in
 * `stats.md` recomputable from the user's own export, which VISION §5 requires
 * of any statistic the app is willing to show.
 *
 * [presentDays] is the presence evidence (VISION §7). A date missing from it is
 * a day the app never saw — unknown, not failed.
 */
class SuiteHistory(
    val habits: List<Habit>,
    checks: List<Check>,
    val presentDays: Set<LocalDate>,
    /**
     * Days whose run was edited after they closed — the log's `# amended`
     * marker (VISION §4.2). Evidence like [presentDays], never a verdict.
     */
    val amendedDays: Set<LocalDate> = emptySet()
) {
    private val byHabitAndDate: Map<Pair<Long, LocalDate>, Check> =
        checks.associateBy { it.habitId to it.date }

    private val byDate: Map<LocalDate, List<Check>> = checks.groupBy { it.date }

    private val byHabit: Map<Long, List<Check>> =
        checks.groupBy { it.habitId }.mapValues { (_, rows) -> rows.sortedBy { it.date } }

    private val habitsById: Map<Long, Habit> = habits.associateBy { it.id }

    /**
     * Open-ended skips, indexed by test.
     *
     * A `[~ skip] until:` is stored as **one** row on the day the user tapped —
     * one interaction, one fact — and the days it covers are expanded here on
     * read. Materialising a row per covered day would have been simpler and
     * would have lied twice: it would claim the user interacted on days that
     * have not happened yet, and those rows would make [ran] true for days the
     * app never saw, quietly turning a week away into a week of full coverage.
     */
    private val skipWindows: Map<Long, List<Check>> = checks
        .filter { it.isSkipWindow }
        .groupBy { it.habitId }

    fun habit(id: Long): Habit? = habitsById[id]

    /**
     * This test's row on this day: the one the user wrote, or the skip window
     * covering the day, expanded into an ordinary skip.
     */
    fun check(habitId: Long, date: LocalDate): Check? {
        byHabitAndDate[habitId to date]?.let { return it }
        val window = skipWindows[habitId]?.firstOrNull { w ->
            !date.isBefore(w.date) && w.until != null && !date.isAfter(w.until)
        } ?: return null
        return Check(
            habitId = habitId,
            date = date,
            state = CheckState.SKIP,
            note = window.note,
            until = window.until
        )
    }

    /** The rows literally written on this day — presence evidence, not coverage. */
    fun checksOn(date: LocalDate): List<Check> = byDate[date].orEmpty()

    /** This test's rows in date order — the input of every per-test statistic. */
    fun checksFor(habitId: Long): List<Check> = byHabit[habitId].orEmpty()

    /**
     * Did this logical day run at all?
     *
     * Presence is the answer, but a check row written *on* that date counts too:
     * a row can only exist because someone tapped, so a day with results and no
     * presence row is a data inconsistency, not a `no run` day — and the honest
     * reading of "there are results here" is that somebody was there. Days a
     * skip window merely covers do not count: nobody was there on those.
     */
    fun ran(date: LocalDate): Boolean =
        date in presentDays || byDate[date]?.isNotEmpty() == true

    /** The tests in the suite on [date], in file order. */
    fun activeOn(date: LocalDate): List<Habit> =
        habits.filter { it.isActiveOn(date) }.sortedBy { it.position }

    /** Was this day's run edited after it closed? */
    fun amended(date: LocalDate): Boolean = date in amendedDays

    /** The earliest day the suite existed, or null on an empty suite. */
    fun suiteStart(): LocalDate? = habits.minOfOrNull { it.createdAt }

    /** Passes of one test inside one ISO week — the quota's numerator. */
    fun passesInWeek(habitId: Long, week: IsoWeek): Int =
        checksFor(habitId).count { it.state == CheckState.PASS && IsoWeek.of(it.date) == week }

    companion object {
        val Empty = SuiteHistory(emptyList(), emptyList(), emptySet())
    }
}
