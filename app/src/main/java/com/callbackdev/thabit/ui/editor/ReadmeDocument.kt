package com.callbackdev.thabit.ui.editor

import android.content.res.Resources
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.BuildResult
import com.callbackdev.thabit.domain.Health
import com.callbackdev.thabit.domain.StreakUnit
import com.callbackdev.thabit.domain.Streaks
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.format.TableAlign
import com.callbackdev.thabit.ui.format.TableCell
import com.callbackdev.thabit.ui.format.TableColumn
import com.callbackdev.thabit.ui.format.markdownTable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * `README.md` — the day in prose, and the app's plain-language layer.
 *
 * A real repository's README is the human summary of the machine content, which
 * is exactly the job here: `habits.test` is the suite, `habits_history.diff` is
 * the history, and this says what both of them mean in sentences.
 *
 * **Fully localized, headings included** (VISION §1.3): a README is prose, not
 * source, so the keys-stay-English rule does not apply to it. That is also why
 * this builder is the one document in the app that takes a [Resources] instead
 * of composing its own strings — its words *are* resources. The tests assert the
 * rendered prose in both languages, which is the same promise the other
 * documents keep by being pure values.
 *
 * Its second job is the harder one (VISION §3.3.7): **whatever CI term the other
 * files are showing right now, this tab says it in a sentence.** Two rules keep
 * it from rotting into a manual — the gloss lives inside the prose that is
 * already there (there is no glossary section), and only terms *currently on
 * screen* get one. A word the app is not showing needs no explanation.
 */
object ReadmeDocument {

    const val FILE_NAME: String = "README.md"

    /** Below this, the app is willing to say a test is not holding — factually. */
    private const val LOW_HEALTH = 0.5

    /** A run worth a sentence. Shorter than this is a day or two, not a streak. */
    private const val STREAK_WORTH_MENTIONING = 3

    fun build(
        history: SuiteHistory,
        today: LocalDate,
        weekStartsOn: DayOfWeek,
        locale: Locale,
        resources: Resources
    ): List<String> = buildList {
        fun s(id: Int, vararg args: Any): String = resources.getString(id, *args)
        fun plural(id: Int, count: Int, vararg args: Any): String =
            resources.getQuantityString(id, count, *args)

        val run = Verdicts.dayRun(history, today, today)

        add("# ${title(today, locale)}")

        // ---- ## Today ------------------------------------------------------
        add("")
        add("## ${s(R.string.readme_h_today)}")
        if (history.activeOn(today).isEmpty()) {
            add(s(R.string.readme_empty_suite))
        } else if (run.outcomes.isEmpty()) {
            add(s(R.string.readme_nothing_due))
        } else {
            add(todaySentence(run, resources, ::plural))
            // `[·]` is the one glyph nobody has met anywhere else, so it is
            // glossed the moment an avoid test is on the screen next door.
            run.outcomes
                .firstOrNull { it.state == TestState.HOLDING && it.habit.type == HabitType.AVOID }
                ?.let { add(s(R.string.readme_holds, it.habit.name)) }
        }

        // ---- ## Status -----------------------------------------------------
        val status = statusLines(history, run, today, resources, ::plural)
        if (status.isNotEmpty()) {
            add("")
            add("## ${s(R.string.readme_h_status)}")
            status.forEach { add(it) }
        }

        // ---- ## Week -------------------------------------------------------
        add("")
        add("## ${s(R.string.readme_h_week)}")
        addAll(weekTable(history, today, weekStartsOn, locale, resources))

        // ---- footer --------------------------------------------------------
        val commits = commitCount(history, today)
        add("")
        add("*${plural(R.plurals.readme_footer, commits, commits)}*")
    }

    /** The long date, as the reader's language writes it — this is prose. */
    private fun title(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    /**
     * `3 of 6 tests passed, 2 still to do, 1 skipped.`
     *
     * Built clause by clause so a zero never appears: a sentence that says "and
     * 0 skipped" is noise, and the file's rule about zeros holds in prose too.
     */
    private fun todaySentence(
        run: com.callbackdev.thabit.domain.DayRun,
        resources: Resources,
        plural: (Int, Int, Array<out Any>) -> String
    ): String {
        val clauses = mutableListOf(
            plural(R.plurals.readme_today_passed, run.passed, arrayOf(run.passed, run.outcomes.size))
        )
        if (run.pending > 0) {
            clauses += plural(R.plurals.readme_today_pending, run.pending, arrayOf(run.pending))
        }
        if (run.failed > 0) {
            clauses += plural(R.plurals.readme_today_failed, run.failed, arrayOf(run.failed))
        }
        if (run.skipped > 0) {
            clauses += plural(R.plurals.readme_today_skipped, run.skipped, arrayOf(run.skipped))
        }
        return clauses.joinToString(resources.getString(R.string.readme_clause_separator)) + "."
    }

    /**
     * The sentences worth saying about where things stand — and every one of
     * them is also a gloss.
     *
     * `~ build unstable` in the log becomes *four of six passed*; the `# amended`
     * marker becomes a sentence about having corrected a closed day; a day with
     * no commit at all becomes *you did not open the app, and that day is not a
     * failure*. Nothing is said when there is nothing to say: an empty section
     * disappears rather than printing a reassuring nothing.
     */
    private fun statusLines(
        history: SuiteHistory,
        run: com.callbackdev.thabit.domain.DayRun,
        today: LocalDate,
        resources: Resources,
        plural: (Int, Int, Array<out Any>) -> String
    ): List<String> = buildList {
        fun s(id: Int, vararg args: Any): String = resources.getString(id, *args)

        // Today's own verdict, in words rather than as a badge.
        if (run.graded > 0) {
            add(
                when (run.result) {
                    BuildResult.PASSED -> s(R.string.readme_status_all, run.passed)
                    BuildResult.FAILED -> s(R.string.readme_status_none, run.graded)
                    else -> s(R.string.readme_status_some, run.passed, run.graded)
                }
            )
        }

        // Yesterday, which is the verdict the log is showing right now.
        val yesterday = today.minusDays(1)
        val previous = Verdicts.dayRun(history, yesterday, today)
        if (previous.hasCommit && previous.graded > 0) {
            add(s(R.string.readme_yesterday, previous.passed, previous.graded))
        }
        if (history.amended(yesterday)) add(s(R.string.readme_amended))

        // A streak worth mentioning: the longest one running into today.
        history.activeOn(today)
            .map { it to Streaks.current(history, it, today) }
            .filter { (_, streak) -> streak.length >= STREAK_WORTH_MENTIONING }
            .maxByOrNull { (_, streak) -> streak.length }
            ?.let { (habit, streak) ->
                val id = if (streak.unit == StreakUnit.WEEKS) {
                    R.plurals.readme_streak_weeks
                } else {
                    R.plurals.readme_streak_days
                }
                add(plural(id, streak.length, arrayOf(habit.name, streak.length)))
            }

        // The weakest test, stated as a rate and nothing else. No advice, no
        // encouragement: the mechanics are humane, the reporting is honest.
        history.activeOn(today)
            .mapNotNull { habit -> Health.of(history, habit, today)?.let { habit to it } }
            .filter { (_, health) -> health < LOW_HEALTH }
            .minByOrNull { (_, health) -> health }
            ?.let { (habit, health) ->
                add(s(R.string.readme_health_low, habit.name, CodeFormat.percent(health)))
            }

        // A day nobody was there is the app's own "I do not know", and it is the
        // least obvious thing the log does — so it is said out loud (§3.3.8).
        val missing = (1L..7L)
            .map { today.minusDays(it) }
            .filter { date -> date >= (history.suiteStart() ?: today) }
            .firstOrNull { date -> !history.ran(date) && history.activeOn(date).any { it.occursOn(date) } }
        if (missing != null) {
            add(s(R.string.readme_no_run, missing.dayName(Locale.getDefault())))
        }
    }

    /**
     * Seven days as a markdown table, the display week the reader chose.
     *
     * `week_starts` moves this grid (and the heatmap) and nothing else: ISO weeks
     * still decide quotas and records, because a record whose meaning changes
     * with a display preference is not recomputable from an export.
     */
    private fun weekTable(
        history: SuiteHistory,
        today: LocalDate,
        weekStartsOn: DayOfWeek,
        locale: Locale,
        resources: Resources
    ): List<String> {
        val start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStartsOn))
        val rows = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            val label = date.dayName(locale) + " " + date.dayOfMonth
            // Today is bold and live — it is the only row that can still change.
            val day = if (date == today) "**$label**" else label
            when {
                date > today -> listOf(TableCell(day), TableCell("—"), TableCell("—"))
                else -> {
                    val run = Verdicts.dayRun(history, date, today)
                    val closed = date < today
                    when {
                        closed && !run.ran ->
                            listOf(
                                TableCell(day),
                                TableCell("—"),
                                TableCell(resources.getString(R.string.readme_result_not_opened))
                            )
                        run.outcomes.isEmpty() ->
                            listOf(TableCell(day), TableCell("—"), TableCell("—"))
                        else -> listOf(
                            TableCell(day),
                            TableCell("${run.passed}/${run.graded + run.pending}"),
                            TableCell(resources.getString(run.result.word(run)))
                        )
                    }
                }
            }
        }
        return markdownTable(
            columns = listOf(
                TableColumn(resources.getString(R.string.readme_col_day)),
                TableColumn(resources.getString(R.string.readme_col_passed), TableAlign.RIGHT),
                TableColumn(resources.getString(R.string.readme_col_result))
            ),
            rows = rows
        )
    }

    /** The day's verdict as one plain word — never the CI one. */
    private fun BuildResult.word(run: com.callbackdev.thabit.domain.DayRun): Int = when {
        run.graded == 0 -> R.string.readme_result_open
        this == BuildResult.PASSED -> R.string.readme_result_all
        this == BuildResult.FAILED -> R.string.readme_result_none
        else -> R.string.readme_result_some
    }

    private fun commitCount(history: SuiteHistory, today: LocalDate): Int {
        val start = history.suiteStart() ?: return 0
        return Verdicts.runs(history, start, today.minusDays(1), today).count { it.hasCommit }
    }

    private fun LocalDate.dayName(locale: Locale): String =
        dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
}
