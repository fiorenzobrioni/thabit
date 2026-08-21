package com.callbackdev.thabit.ui.editor

import com.callbackdev.thabit.domain.Health
import com.callbackdev.thabit.domain.Quotas
import com.callbackdev.thabit.domain.StreakUnit
import com.callbackdev.thabit.domain.Streaks
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import com.callbackdev.thabit.ui.format.CodeFormat
import java.time.LocalDate
import java.time.LocalTime

/**
 * `habits.test` as a document — the file the screen renders, worked out with no
 * Compose in sight.
 *
 * The screen is the app's most-read surface and the one whose every word is a
 * promise: the header's arithmetic, the live detail in each comment, the honest
 * empty state. Keeping the document a pure value means those words are asserted
 * character by character in plain JVM tests instead of hunted for in a rendered
 * tree — and it is the same split the domain uses, one layer down.
 *
 * The **comment channel stays English** (VISION §1.3): comments are source, not
 * chrome, so they are built here as literal strings. The localized half — what a
 * screen reader says — travels beside them as a structured [RowDetail] the
 * renderer turns into a sentence in the reader's language.
 */
data class SuiteDocument(
    /** The day the suite is running: the logical one, not necessarily the wall one. */
    val logicalDate: LocalDate,
    val wallDate: LocalDate,
    val dayEnds: LocalTime,
    val passed: Int,
    val failed: Int,
    val pending: Int,
    val skipped: Int,
    /** Tests due today, in file order. */
    val due: List<TestRow>,
    /** Tests in the suite that today does not ask for — commented out, not hidden. */
    val notDue: List<NotDueRow>,
    /** Live tests in the suite, for the status bar. */
    val suiteSize: Int
) {
    val isEmpty: Boolean get() = suiteSize == 0

    /** The denominator of the status bar's `3/6 passed`: due tests minus skips. */
    val graded: Int get() = passed + failed + pending

    /**
     * `suite 2026-08-20 — 3 passed · 2 pending · 1 skipped`.
     *
     * `passed` is always stated, even at zero: it is the number the reader came
     * for. The others appear only when they happened, because a line of zeros is
     * noise, not honesty.
     */
    val suiteComment: String
        get() = buildString {
            append("suite ")
            append(CodeFormat.date(logicalDate))
            append(" — ")
            val parts = mutableListOf("$passed passed")
            if (failed > 0) parts += "$failed failed"
            if (pending > 0) parts += "$pending pending"
            if (skipped > 0) parts += "$skipped skipped"
            append(parts.joinToString(" · "))
        }

    /**
     * `logical day 2026-08-20 — ends 03:00`, and **only** when the logical date
     * has drifted from the wall date.
     *
     * At one in the morning with `day_ends: 03:00` the phone says the 21st while
     * the suite is still the 20th's; undeclared, that honesty looks like a bug
     * (VISION §4.1). At the default midnight the two never diverge and the line
     * never appears, which is the point: the mechanism is invisible until it
     * matters.
     */
    val logicalDayComment: String?
        get() = if (logicalDate == wallDate) {
            null
        } else {
            "logical day ${CodeFormat.date(logicalDate)} — ends ${CodeFormat.time(dayEnds)}"
        }

    /** `2 tests not due today — [show]` / `[hide]`. */
    fun notDueComment(expanded: Boolean): String? {
        if (notDue.isEmpty()) return null
        val noun = if (notDue.size == 1) "test" else "tests"
        return "${notDue.size} $noun not due today — ${if (expanded) "[hide]" else "[show]"}"
    }

    companion object {

        /** The file's own name, shown as its first line until the tabs arrive (Fase 7). */
        const val FILE_NAME: String = "habits.test"

        /**
         * Whether the `README.md` editor tab exists yet (Fase 7).
         *
         * The empty state is supposed to point at it, and until it is there that
         * line would send a first-time reader to a tab that does not exist —
         * which is the file lying, on the very first sentence the app ever says.
         * Flipped in Fase 7, and the hint comes back with it.
         */
        const val README_TAB_SHIPPED: Boolean = false

        /**
         * The empty suite, which is also the first sentence the app ever says.
         *
         * First launch is the one moment the screen is *only* metaphor, with no
         * checklist on it yet to carry the meaning — so the empty file points at
         * the FAB and at the tab that speaks plainly (VISION §3.3.7, §4.1).
         */
        fun emptyHints(readmeTab: Boolean = README_TAB_SHIPPED): List<String> = buildList {
            add("no tests in the suite yet")
            add("")
            add("tap + to add your first test")
            if (readmeTab) add("the README tab says what a test is here")
        }

        /**
         * A `[+N]` control is offered only when it is genuinely a shortcut: at
         * most this many taps from nothing to the assert holding. Thirteen taps
         * is a prompt, not a shortcut.
         */
        const val MAX_INCREMENT_TAPS: Int = 12

        fun of(
            history: SuiteHistory,
            logicalDate: LocalDate,
            wallDate: LocalDate,
            dayEnds: LocalTime = LocalTime.MIDNIGHT
        ): SuiteDocument {
            val run = Verdicts.dayRun(history, logicalDate, logicalDate)
            val active = history.activeOn(logicalDate)
            val dueIds = run.outcomes.map { it.habit.id }.toSet()

            return SuiteDocument(
                logicalDate = logicalDate,
                wallDate = wallDate,
                dayEnds = dayEnds,
                passed = run.passed,
                failed = run.failed,
                pending = run.pending,
                skipped = run.skipped,
                due = run.outcomes.map { row(history, it, logicalDate) },
                notDue = active.filter { it.id !in dueIds }
                    .map { notDueRow(history, it, logicalDate) },
                suiteSize = active.size
            )
        }

        private fun row(
            history: SuiteHistory,
            outcome: TestOutcome,
            date: LocalDate
        ): TestRow {
            val habit = outcome.habit
            val quota = Quotas.weekOf(history, habit, date, date)
            val value = outcome.value ?: 0.0
            val target = habit.assert?.target

            val detail: RowDetail = when {
                outcome.state == TestState.SKIP ->
                    RowDetail.Skipped(outcome.note, outcome.check?.until?.takeIf { it > date })
                outcome.state == TestState.FAIL ->
                    RowDetail.Failed(outcome.check?.at, outcome.note)
                outcome.state == TestState.HOLDING -> RowDetail.Holding
                habit.type == HabitType.COUNTER && target != null ->
                    RowDetail.Counter(value, target, habit.assert.unit, outcome.state == TestState.PASS)
                outcome.state == TestState.PASS -> RowDetail.Passed(outcome.check?.at)
                quota != null -> RowDetail.Quota(quota.done, quota.target)
                else -> RowDetail.Pending
            }

            return TestRow(
                habitId = habit.id,
                name = displayName(habit),
                type = habit.type,
                state = outcome.state,
                comment = comment(detail),
                detail = detail,
                // The test's own unit, not today's: a skipped counter still
                // counts pages, and its prompt has to know that.
                unit = habit.assert?.unit?.takeIf { it.isNotBlank() },
                incrementStep = incrementStep(habit, outcome.state),
                spec = spec(history, habit, date)
            )
        }

        /** The live detail the comment channel carries, in English, as source. */
        private fun comment(detail: RowDetail): String? = when (detail) {
            is RowDetail.Passed -> detail.at?.let { CodeFormat.time(it) }
            is RowDetail.Counter ->
                if (detail.passed) "${CodeFormat.number(detail.value)} ${detail.unit}".trim()
                else "${CodeFormat.fraction(detail.value, detail.target)} ${detail.unit}".trim()
            is RowDetail.Skipped -> buildString {
                append("skip")
                detail.note?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                detail.until?.let { append(" until ").append(CodeFormat.date(it)) }
            }
            RowDetail.Holding -> "holds — asserts at commit"
            is RowDetail.Failed -> buildString {
                append("failed")
                detail.at?.let { append(" ").append(CodeFormat.time(it)) }
                detail.note?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            }
            is RowDetail.Quota -> "${detail.done}/${detail.target} this week"
            RowDetail.Pending -> null
        }

        /**
         * A test the schedule does not ask for today is rendered as a
         * **commented-out line**, not hidden and not faked into a checkbox.
         *
         * It is the reading a code editor would give it, and it keeps the file
         * honest twice over: the test is visibly still in the suite, and it
         * visibly asks nothing today — with the reason (`when:` or the week's
         * quota) stated instead of implied.
         */
        private fun notDueRow(history: SuiteHistory, habit: Habit, date: LocalDate): NotDueRow {
            val quota = Quotas.weekOf(history, habit, date, date)
            val reason = if (quota != null) {
                "${quota.done}/${quota.target} this week — done"
            } else {
                "when: ${habit.schedule.format()}"
            }
            return NotDueRow(habit.id, displayName(habit), reason, spec(history, habit, date))
        }

        private fun displayName(habit: Habit): String =
            habit.emoji?.takeIf { it.isNotBlank() }?.let { "${habit.name} $it" } ?: habit.name

        /** `[+1]` for the counters where a tap is genuinely faster than a prompt. */
        private fun incrementStep(habit: Habit, state: TestState): Double? {
            if (habit.type != HabitType.COUNTER) return null
            if (state == TestState.SKIP) return null
            val assert = habit.assert ?: return null
            if (assert.target / assert.step > MAX_INCREMENT_TAPS) return null
            return assert.step
        }

        private fun spec(history: SuiteHistory, habit: Habit, date: LocalDate): TestSpec {
            val streak = Streaks.current(history, habit, date)
            return TestSpec(
                schedule = habit.schedule.format(),
                assertText = habit.assert?.let {
                    "${it.unit.ifBlank { "value" }} >= ${CodeFormat.number(it.target)}"
                },
                remind = habit.remindAt?.let { CodeFormat.time(it) },
                streak = streak.length,
                streakUnit = streak.unit,
                health = Health.of(history, habit, date),
                isQuota = habit.schedule is Schedule.Quota
            )
        }
    }
}

/** One test line of the file: the checkbox, the name, and the live detail. */
data class TestRow(
    val habitId: Long,
    /** User data — the name as typed, with the optional emoji appended. */
    val name: String,
    val type: HabitType,
    val state: TestState,
    /** English, dimmed, trailing: `07:12`, `12/30 reps`, `skip: rest day`. */
    val comment: String?,
    /** The same fact, structured, so a screen reader can be told it in its language. */
    val detail: RowDetail,
    /**
     * What a counter counts — `pages`, `reps` — or null for the other types.
     *
     * A property of the test and not of the day, which is the point: it used to
     * be read out of [RowDetail.Counter], so a **skipped** counter lost it and
     * its prompt opened as the anonymous `> value:` instead of `> pages:`.
     */
    val unit: String?,
    /** Step of the `[+N]` control, or null when the row does not offer one. */
    val incrementStep: Double?,
    val spec: TestSpec
)

/**
 * A test still in the suite that today does not ask for.
 *
 * It carries its [spec] like any other row: a commented-out line is about
 * *today's run*, not about the test's existence, so the same expansion has to
 * open on it. Without it, a `mon,wed,fri` test could not be read, edited or
 * archived on a Tuesday — a third of the suite would be out of reach on any
 * given day, which is not what "commented out, not hidden" was supposed to mean.
 */
data class NotDueRow(
    val habitId: Long,
    val name: String,
    /** Why it is quiet today: `when: mon,wed,fri`, or the week's quota already met. */
    val reason: String,
    val spec: TestSpec
)

/** The expanded spec of a test: what the file would say if you unfolded the line. */
data class TestSpec(
    val schedule: String,
    val assertText: String?,
    val remind: String?,
    val streak: Int,
    val streakUnit: StreakUnit,
    /** 0..1, or null when the app has nothing to go on yet — never a fake zero. */
    val health: Double?,
    val isQuota: Boolean
)

/**
 * The live detail of a row, structured.
 *
 * The comment channel says it in English because it is source; this says the
 * same thing in a shape the renderer can turn into a localized sentence for a
 * screen reader. Two renderings, one fact — never two facts.
 */
sealed interface RowDetail {
    data class Passed(val at: LocalTime?) : RowDetail
    data class Counter(
        val value: Double,
        val target: Double,
        val unit: String,
        val passed: Boolean
    ) : RowDetail
    data class Skipped(val note: String?, val until: LocalDate?) : RowDetail
    data class Failed(val at: LocalTime?, val note: String?) : RowDetail
    data class Quota(val done: Int, val target: Int) : RowDetail
    data object Holding : RowDetail
    data object Pending : RowDetail
}
