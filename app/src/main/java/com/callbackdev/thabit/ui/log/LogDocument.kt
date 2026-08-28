package com.callbackdev.thabit.ui.log

import androidx.annotation.StringRes
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.BuildResult
import com.callbackdev.thabit.domain.CommitHash
import com.callbackdev.thabit.domain.DayRun
import com.callbackdev.thabit.domain.IsoWeek
import com.callbackdev.thabit.domain.Quotas
import com.callbackdev.thabit.domain.Records
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * `habits_history.diff` as a document — the CI dashboard written as a git log.
 *
 * Same split as `habits.test` and `settings.config`: the file is a **pure
 * value** so its every word is asserted in plain JVM tests, and the screen does
 * nothing but draw it and hand taps back.
 *
 * Nothing here is stored. Every commit, every verdict and every week rate is
 * derived from the check rows on read (VISION §6.8), which is what makes
 * `--amend` free: change a row and the log recomputes, including the days after
 * it. The one thing that *is* persisted is [LogEntry.Commit.amended] — because
 * "this day was edited after it closed" is a fact about the editing, not a
 * verdict about the day, and it has to survive the amendment being taken back.
 *
 * Two values are deliberately **not** pre-formatted into strings here: the
 * commit's date and the amend window's closing time. Both are chrome the reader
 * reads in their own language (VISION §1.3, §4.2 — "localized clock"), so they
 * travel as [LocalDate]/[LocalTime] and the renderer formats them. Everything
 * else on the screen is source, and source is English.
 */
data class LogDocument(
    /** The logical day the log is being read on — the uncommitted working tree. */
    val today: LocalDate,
    /** `3 passed · 2 pending · 1 skipped`, or null when today asks nothing. */
    val todaySummary: String?,
    /** Commits and week separators, newest first. */
    val entries: List<LogEntry>,
    val commitCount: Int,
    /** The newest commit's hash — the status bar's `HEAD → 9e31c7a`. */
    val head: String?
) {
    val isEmpty: Boolean get() = commitCount == 0

    val commits: List<LogEntry.Commit>
        get() = entries.filterIsInstance<LogEntry.Commit>()

    fun commitOn(date: LocalDate): LogEntry.Commit? = commits.firstOrNull { it.date == date }

    companion object {

        /** The file's own name, shown as its first line until the tabs arrive (Fase 7). */
        const val FILE_NAME: String = "habits_history.diff"

        /**
         * `On branch main — changes not yet committed (today)`.
         *
         * git's own sentence, and git translates it: under `LANG=it_IT` the real
         * thing prints `Sul branch main` and keeps `branch`, `main` and `commit`
         * exactly where they are. That is the register rule with the metaphor's
         * own tool as the worked example (VISION §1.3, Fase 15), so this line is
         * a resource and its nouns are not.
         */
        @StringRes val BRANCH_LINE: Int = R.string.log_branch

        /** The shortest run the `longest-streak` tag is willing to call a record. */
        const val MIN_TAGGED_STREAK: Int = 2

        /**
         * The empty log, which is also a promise about when it stops being empty.
         *
         * A first-time reader arrives here with a suite that has never closed a
         * day: the honest thing is not "nothing here" but *why*, and the fact
         * that a commit is a day ending rather than something they must do.
         */
        fun emptyHints(hasSuite: Boolean): List<Int> = buildList {
            add(R.string.log_empty_none)
            add(BLANK_LINE)
            if (hasSuite) {
                add(R.string.log_empty_suite)
            } else {
                add(R.string.log_empty_no_suite)
            }
        }

        /** The blank line between the two, spelled the same way as the suite's. */
        const val BLANK_LINE: Int = 0

        fun of(
            history: SuiteHistory,
            logicalDate: LocalDate,
            dayEnds: LocalTime = LocalTime.MIDNIGHT
        ): LogDocument {
            val todayRun = Verdicts.dayRun(history, logicalDate, logicalDate)
            val start = history.suiteStart()
            val runs = if (start == null) {
                emptyList()
            } else {
                Verdicts.runs(history, start, logicalDate.minusDays(1), logicalDate)
                    // A day with tests due and no run produces **no commit at
                    // all**: Jenkins does not paint red the days nobody pushed,
                    // and the history does not gain lines the user never wrote
                    // (VISION §3.3.8, §4.2).
                    .filter { it.hasCommit }
                    .sortedByDescending { it.date }
            }

            return LogDocument(
                today = logicalDate,
                todaySummary = todayRun.takeIf { it.outcomes.isNotEmpty() }?.let { summary(it) },
                entries = entries(history, runs, logicalDate, dayEnds),
                commitCount = runs.size,
                head = runs.firstOrNull()?.let { CommitHash.of(it.date) }
            )
        }

        /**
         * The log's spine: every commit, with its week's separator **under** the
         * block of commits that belong to it.
         *
         * Under and not over, because the file reads downwards into the past:
         * the separator closes the week the way a `---` closes a section, and
         * the eye meets the days first and the week's arithmetic after them —
         * the same order the VISION mock draws.
         */
        private fun entries(
            history: SuiteHistory,
            runs: List<DayRun>,
            today: LocalDate,
            dayEnds: LocalTime
        ): List<LogEntry> {
            if (runs.isEmpty()) return emptyList()
            val tags = tags(history, runs, today)
            val entries = mutableListOf<LogEntry>()
            var current: IsoWeek? = null

            runs.forEach { run ->
                val week = IsoWeek.of(run.date)
                if (current != null && week != current) {
                    entries += weekEntry(history, runs, current, today)
                }
                current = week
                entries += commit(history, run, tags[run.date].orEmpty(), today, dayEnds)
            }
            current?.let { entries += weekEntry(history, runs, it, today) }
            return entries
        }

        /**
         * Records as git tags, on the commit that earned them (VISION §4.2).
         *
         * A perfect week is a fact about seven days, so it lands on the newest
         * commit of that week — the day it was completed on. There are exactly
         * two tags in this app and neither is a score (§6.4).
         */
        private fun tags(
            history: SuiteHistory,
            runs: List<DayRun>,
            today: LocalDate
        ): Map<LocalDate, List<String>> {
            val byDate = mutableMapOf<LocalDate, MutableList<String>>()
            val start = history.suiteStart() ?: return emptyMap()

            Records.perfectWeeks(history, start, today.minusDays(1), today).forEach { record ->
                val newest = runs.firstOrNull { IsoWeek.of(it.date) == record.week }?.date
                if (newest != null) byDate.getOrPut(newest) { mutableListOf() } += "perfect-week"
            }
            Records.longestStreak(history, today)
                ?.streak
                // A run of one is not a run. Everybody's first day would wear
                // this tag, and a record that is also a tie between every day
                // the suite has ever seen is not a record — it is an arbitrary
                // pick among equals (VISION §6.4: no score, and nothing awarded
                // for showing up).
                ?.takeIf { it.length >= MIN_TAGGED_STREAK }
                ?.to
                ?.let { end ->
                    if (runs.any { it.date == end }) {
                        byDate.getOrPut(end) { mutableListOf() } += "longest-streak"
                    }
                }
            return byDate
        }

        private fun commit(
            history: SuiteHistory,
            run: DayRun,
            tags: List<String>,
            today: LocalDate,
            dayEnds: LocalTime
        ): LogEntry.Commit = LogEntry.Commit(
            date = run.date,
            hash = CommitHash.of(run.date),
            tags = tags,
            message = "suite: ${summary(run)}",
            verdict = verdict(run),
            amended = history.amended(run.date),
            // Yesterday, and only yesterday: the window shrinks to zero as today
            // runs out and there is nothing to declare on any other day.
            amendableUntil = dayEnds.takeIf { run.date == today.minusDays(1) },
            rows = rows(history, run)
        )

        /** `4/6 passed · 1 skipped` — the day's arithmetic, in one phrase. */
        private fun summary(run: DayRun): String = buildString {
            append("${run.passed}/${run.outcomes.size} passed")
            if (run.pending > 0) append(" · ${run.pending} pending")
            if (run.skipped > 0) append(" · ${run.skipped} skipped")
        }

        /**
         * `~ build unstable (4/5 · 1 skipped)` — Jenkins semantics, and the
         * arithmetic that always travels with the word (VISION §3.3.7).
         *
         * The verdict's denominator is the **graded** tests, so a skip is not a
         * failure; the message line above it counts every test the day asked
         * for. The two numbers differ on purpose and both are stated.
         */
        private fun verdict(run: DayRun): LogEntry.Verdict? {
            if (!run.result.hasBadge) return null
            val word = when (run.result) {
                BuildResult.PASSED -> "✓ build passed"
                BuildResult.UNSTABLE -> "~ build unstable"
                else -> "✗ build failed"
            }
            val arithmetic = buildString {
                append(run.fraction)
                if (run.skipped > 0) append(" · ${run.skipped} skipped")
            }
            return LogEntry.Verdict("$word ($arithmetic)", run.result)
        }

        /**
         * The day as a diff: one line per test, plus the suite's own changes.
         *
         * Adding or archiving a test is a change to the file under test, so it
         * belongs in that day's diff exactly like a result does — and it is the
         * only place the history ever explains why a test appears or stops
         * appearing (VISION §4.2).
         */
        private fun rows(history: SuiteHistory, run: DayRun): List<CommitRow> {
            val rows = mutableListOf<CommitRow>()
            history.habits
                .filter { it.createdAt == run.date }
                .sortedBy { it.position }
                .forEach { rows += suiteChange(it, added = true) }
            history.habits
                .filter { it.archivedAt == run.date }
                .sortedBy { it.position }
                .forEach { rows += suiteChange(it, added = false) }
            run.outcomes.forEach { outcome -> rows += testRow(outcome) }
            return rows
        }

        private fun suiteChange(habit: Habit, added: Boolean): CommitRow = CommitRow(
            sign = if (added) DiffSign.ADD else DiffSign.DEL,
            label = if (added) "test added: \"${habit.name}\"" else "test archived: \"${habit.name}\"",
            detail = null,
            test = null
        )

        private fun testRow(outcome: TestOutcome): CommitRow {
            val habit = outcome.habit
            val glyph = when (outcome.state) {
                TestState.PASS -> "[x]"
                TestState.FAIL -> if (habit.type == HabitType.AVOID) "[!]" else "[ ]"
                TestState.SKIP -> "[~]"
                else -> "[ ]"
            }
            val name = habit.emoji?.takeIf { it.isNotBlank() }
                ?.let { "${habit.name} $it" } ?: habit.name
            return CommitRow(
                sign = when (outcome.state) {
                    TestState.PASS -> DiffSign.ADD
                    TestState.SKIP -> DiffSign.SKIP
                    else -> DiffSign.DEL
                },
                label = "$glyph $name",
                detail = detail(outcome),
                test = RowTest(
                    habitId = habit.id,
                    name = name,
                    type = habit.type,
                    state = outcome.state,
                    unit = habit.assert?.unit?.takeIf { it.isNotBlank() }
                )
            )
        }

        /** The trailing fact: when it passed, how much was counted, why it was skipped. */
        private fun detail(outcome: TestOutcome): String? {
            val assert = outcome.habit.assert
            val value = outcome.value
            return when (outcome.state) {
                TestState.PASS ->
                    if (assert != null && value != null) {
                        "${CodeFormat.number(value)} ${assert.unit}".trim()
                    } else {
                        outcome.check?.at?.let { CodeFormat.time(it) }
                    }
                TestState.FAIL ->
                    if (assert != null && value != null) {
                        "${CodeFormat.fraction(value, assert.target)} ${assert.unit}".trim()
                    } else {
                        outcome.note?.takeIf { it.isNotBlank() }
                    }
                TestState.SKIP -> outcome.note?.takeIf { it.isNotBlank() }
                else -> null
            }
        }

        /**
         * The week, as a diff of its own.
         *
         * The rate counts the days that actually ran — `no run` days are absent
         * from the commits and therefore from the arithmetic, which is the whole
         * point of §3.3.8: the week reports what happened, not what is missing.
         */
        private fun weekEntry(
            history: SuiteHistory,
            runs: List<DayRun>,
            week: IsoWeek,
            today: LocalDate
        ): LogEntry.Week {
            val previous = IsoWeek.of(week.start.minusWeeks(1))
            return LogEntry.Week(
                week = week,
                passed = runs.filter { IsoWeek.of(it.date) == week }.sumOf { it.passed },
                graded = runs.filter { IsoWeek.of(it.date) == week }.sumOf { it.graded },
                previousPassed = runs.filter { IsoWeek.of(it.date) == previous }.sumOf { it.passed },
                previousGraded = runs.filter { IsoWeek.of(it.date) == previous }.sumOf { it.graded },
                quotas = Quotas.verdictsForWeek(history, week, today).map { quota ->
                    QuotaLine(
                        name = history.habit(quota.habitId)?.name.orEmpty(),
                        done = quota.done,
                        target = quota.target,
                        met = quota.met,
                        closed = quota.closed
                    )
                }
            )
        }
    }
}

/** One entry of the log: a commit, or the separator that closes a week. */
sealed interface LogEntry {

    /** One committed day. */
    data class Commit(
        val date: LocalDate,
        val hash: String,
        /** `perfect-week`, `longest-streak` — records, rendered as git tags. */
        val tags: List<String>,
        /** `suite: 4/6 passed · 1 skipped`. */
        val message: String,
        /** Null when the day graded nothing: no check ran, so no badge is earned. */
        val verdict: Verdict?,
        val amended: Boolean,
        /**
         * When the grace runs out, on the one day that still has any — the
         * `# still editable until 03:00` the commit carries so **the window is
         * declared and not discovered** (VISION §4.2). A tappable row is
         * invisible: nobody touches a line hoping it might open.
         */
        val amendableUntil: LocalTime?,
        val rows: List<CommitRow>
    ) : LogEntry {
        val amendable: Boolean get() = amendableUntil != null

        /** `commit 9e31c7a  (tag: perfect-week)  # amended`. */
        val headline: String
            get() = buildString {
                append("commit ").append(hash)
                tags.forEach { append("  (tag: ").append(it).append(")") }
                if (amended) append("  # amended")
            }
    }

    /** `--- week 34 · 89% passed (+6% vs week 33) ---`. */
    data class Week(
        val week: IsoWeek,
        val passed: Int,
        val graded: Int,
        val previousPassed: Int,
        val previousGraded: Int,
        val quotas: List<QuotaLine>
    ) : LogEntry {
        val rate: Double? get() = if (graded == 0) null else passed.toDouble() / graded
        private val previousRate: Double?
            get() = if (previousGraded == 0) null else previousPassed.toDouble() / previousGraded

        /** `week 34 · 89% passed`, or just `week 34` when nothing was graded. */
        val label: String
            get() = rate?.let { "$week · ${CodeFormat.percent(it)} passed" } ?: "$week"

        /**
         * `+6%` / `-4%` against the week before, or null when there is nothing
         * to compare with — a first week has no delta, and inventing a `+89%`
         * against a week that never existed would be the file making one up.
         */
        val delta: String?
            get() {
                val now = rate ?: return null
                val before = previousRate ?: return null
                val points = ((now - before) * 100).roundToInt()
                return (if (points >= 0) "+" else "-") + "${abs(points)}%"
            }

        val deltaPositive: Boolean get() = (rate ?: 0.0) >= (previousRate ?: 0.0)

        val previousLabel: String get() = IsoWeek.of(week.start.minusWeeks(1)).toString()
    }

    /** The Jenkins line: the word, and the numbers that explain it. */
    data class Verdict(val text: String, val result: BuildResult)
}

/** How a line of the diff is signed. */
enum class DiffSign(val glyph: String) { ADD("+"), DEL("-"), SKIP("~") }

/** One line inside a commit: a test's result, or a change to the suite itself. */
data class CommitRow(
    val sign: DiffSign,
    /** `[x] meditate 10 min`, or `test added: "journal"`. */
    val label: String,
    /** `07:03`, `31 pages`, `rest day` — the trailing detail, or null. */
    val detail: String?,
    /** Null on the rows that are suite changes rather than results. */
    val test: RowTest?
) {
    /** The whole line, as the diff writes it — what the tests assert. */
    val text: String
        get() = buildString {
            append(sign.glyph).append(' ').append(label)
            detail?.let { append("  # ").append(it) }
        }
}

/** What an amendable row needs to answer a tap, mirroring the suite's own row. */
data class RowTest(
    val habitId: Long,
    val name: String,
    val type: HabitType,
    val state: TestState,
    val unit: String?
)

/** `quota: run 5k 2/3 ✗` — a quota's verdict, which belongs to the week. */
data class QuotaLine(
    val name: String,
    val done: Int,
    val target: Int,
    val met: Boolean,
    val closed: Boolean
) {
    /**
     * An open week that is still short gets **no glyph at all**: it has not
     * failed, it is simply not finished. A quota never fails a day, and it does
     * not fail a week until the week is over (VISION §5).
     */
    val text: String
        get() = buildString {
            append("quota: ").append(name).append(' ').append(done).append('/').append(target)
            when {
                met -> append(" ✓")
                closed -> append(" ✗")
            }
        }
}
