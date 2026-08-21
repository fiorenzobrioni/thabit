package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate

/**
 * A test's state inside one day's run.
 *
 * [PENDING] and [HOLDING] only exist while the day is open — they are the
 * working tree. At the boundary a pending test resolves to [FAIL] and a holding
 * avoid test resolves to [PASS], both by definition and without anything being
 * written (VISION §7: the commit is a boundary, not a write).
 */
enum class TestState {
    PASS,
    FAIL,
    SKIP,

    /** Still to do, and the day is still open. */
    PENDING,

    /**
     * An avoid test, untouched, on an open day: it asserts an absence, so it is
     * holding and will pass at the commit unless the user breaks it. Its own
     * glyph `[·]` exists because on the widget there is no comment channel to
     * tell it from [PENDING] (VISION §4.1).
     */
    HOLDING;

    /** Graded states are the ones that enter a denominator. */
    val isGraded: Boolean get() = this == PASS || this == FAIL
}

/** The Jenkins verdict of one day (VISION §2, §5). */
enum class BuildResult {
    /** Every graded test passed. */
    PASSED,

    /** Some passed, some failed. */
    UNSTABLE,

    /** Nothing graded passed. */
    FAILED,

    /**
     * Nothing to grade: no test was due, or everything due was skipped. Skips
     * leave every denominator, so a fully skipped day has no verdict to state —
     * the day's counts say what happened instead of a badge inventing one.
     */
    NOT_SCHEDULED,

    /**
     * The app never saw this day (VISION §3.3.8). Not a failure: a build that
     * never started is not a failed build, and Jenkins does not paint a day red
     * because nobody pushed. No commit, blank heatmap cell, out of every
     * denominator, neutral for health — but it does break streaks.
     */
    NO_RUN;

    /** Only these three earn a `✓`/`~`/`✗` badge. */
    val hasBadge: Boolean get() = this == PASSED || this == UNSTABLE || this == FAILED
}

/** One test's line inside a day's run. */
data class TestOutcome(
    val habit: Habit,
    val state: TestState,
    val check: Check? = null
) {
    val value: Double? get() = check?.value
    val note: String? get() = check?.note
}

/** One day's run, entirely derived from the check rows. Nothing here is stored. */
data class DayRun(
    val date: LocalDate,
    val result: BuildResult,
    val outcomes: List<TestOutcome>,
    /** False while the day is today: the verdict is provisional, show counts instead. */
    val closed: Boolean,
    /** Did the app see this day at all? */
    val ran: Boolean
) {
    val passed: Int get() = outcomes.count { it.state == TestState.PASS }
    val failed: Int get() = outcomes.count { it.state == TestState.FAIL }
    val skipped: Int get() = outcomes.count { it.state == TestState.SKIP }
    val pending: Int get() = outcomes.count { it.state == TestState.PENDING || it.state == TestState.HOLDING }

    /** The denominator: due tests minus skips, and minus what is still open. */
    val graded: Int get() = outcomes.count { it.state.isGraded }

    /** True when the day earns a line in `habits_history.diff`. */
    val hasCommit: Boolean get() = ran && outcomes.isNotEmpty()

    /** `4/6` — the arithmetic that always travels with the verdict (VISION §3.3.7). */
    val fraction: String get() = "$passed/$graded"
}

/**
 * Every verdict in the app, computed on read.
 *
 * Nothing here is ever persisted (VISION §6.8): a day's build result derives
 * from its stored checks whenever asked, so there is no midnight mutation to get
 * wrong, no streak counter to corrupt, and `--amend` recomputes everything for
 * free.
 */
object Verdicts {

    /**
     * The tests due on [date], with the state each is in.
     *
     * The quota rule (fixed in [Schedule.Quota]) applies here: on an open day a
     * quota test is listed while its week target is unmet and disappears once it
     * is met, and on a closed day it appears only if the user acted on it. That
     * is what "a quota never fails a day" means concretely — an untouched quota
     * test cannot become a `FAIL` at the boundary the way a daily test does.
     */
    fun outcomesOn(history: SuiteHistory, date: LocalDate, today: LocalDate): List<TestOutcome> {
        val closed = date < today
        val ran = history.ran(date)
        if (closed && !ran) return emptyList()

        return history.activeOn(date).mapNotNull { habit ->
            val check = history.check(habit.id, date)
            if (habit.schedule is Schedule.Quota) {
                quotaOutcome(history, habit, date, check, closed, today)
            } else {
                // A row the user wrote counts even on a day the schedule does not
                // ask for. Editing a test's schedule applies from today, but
                // `occursOn` answers with today's rule, so without this a switch
                // from `daily` to `mon,wed,fri` would erase every Tuesday the user
                // had already passed — the history keeping the rules of its time
                // (VISION §4.5) is the whole point.
                if (!habit.occursOn(date) && check == null) return@mapNotNull null
                TestOutcome(habit, resolve(habit, check, closed), check)
            }
        }
    }

    private fun quotaOutcome(
        history: SuiteHistory,
        habit: Habit,
        date: LocalDate,
        check: Check?,
        closed: Boolean,
        today: LocalDate
    ): TestOutcome? {
        if (check != null) return TestOutcome(habit, resolve(habit, check, closed), check)
        // Untouched: a closed day makes no claim about it at all, and an open day
        // lists it only while the week still wants runs.
        if (closed) return null
        val week = Quotas.weekOf(history, habit, date, today) ?: return null
        return if (week.met) null else TestOutcome(habit, TestState.PENDING, null)
    }

    /**
     * One test's state, given its row (or the absence of one).
     *
     * The two resolutions at the boundary are the whole of the "commit" — a
     * definition, not a write: a pending test becomes a fail because the day it
     * was due is over, and an avoid test becomes a pass because its assertion
     * about absence held all day.
     */
    fun resolve(habit: Habit, check: Check?, closed: Boolean): TestState = when {
        check != null -> when (check.state) {
            CheckState.PASS -> TestState.PASS
            CheckState.FAIL -> TestState.FAIL
            CheckState.SKIP -> TestState.SKIP
            // A counter mid-way: a real number the user typed, not a verdict.
            // Still open while the day is, a fail once the day is over.
            CheckState.PROGRESS -> if (closed) TestState.FAIL else TestState.PENDING
        }
        habit.type == HabitType.AVOID -> if (closed) TestState.PASS else TestState.HOLDING
        else -> if (closed) TestState.FAIL else TestState.PENDING
    }

    /** The full run of one logical day. */
    fun dayRun(history: SuiteHistory, date: LocalDate, today: LocalDate): DayRun {
        val closed = date < today
        val ran = history.ran(date)
        val outcomes = outcomesOn(history, date, today)
        val result = when {
            closed && !ran -> BuildResult.NO_RUN
            else -> buildResult(outcomes)
        }
        return DayRun(date, result, outcomes, closed, ran)
    }

    /**
     * Jenkins semantics over the graded tests.
     *
     * On an open day only what the user has acted on is graded, so today reads
     * "green so far" instead of "failed at 08:00" — the same single formula,
     * with [DayRun.closed] telling the caller the verdict is provisional and
     * must be shown as counts rather than as a badge.
     */
    fun buildResult(outcomes: List<TestOutcome>): BuildResult {
        val graded = outcomes.count { it.state.isGraded }
        if (graded == 0) return BuildResult.NOT_SCHEDULED
        val passed = outcomes.count { it.state == TestState.PASS }
        return when (passed) {
            graded -> BuildResult.PASSED
            0 -> BuildResult.FAILED
            else -> BuildResult.UNSTABLE
        }
    }

    /** The runs of a closed date range, oldest first — the log's spine. */
    fun runs(
        history: SuiteHistory,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): List<DayRun> {
        if (to.isBefore(from)) return emptyList()
        return generateSequence(from) { d -> d.plusDays(1).takeIf { !it.isAfter(to) } }
            .map { dayRun(history, it, today) }
            .toList()
    }
}
