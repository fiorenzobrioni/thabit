package com.callbackdev.thabit.ui.stats

import androidx.annotation.StringRes
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.Coverage
import com.callbackdev.thabit.domain.CoverageReport
import com.callbackdev.thabit.domain.FlakyTest
import com.callbackdev.thabit.domain.FlakyTests
import com.callbackdev.thabit.domain.Health
import com.callbackdev.thabit.domain.Heatmap
import com.callbackdev.thabit.domain.HeatmapGrid
import com.callbackdev.thabit.domain.Outcomes
import com.callbackdev.thabit.domain.Record
import com.callbackdev.thabit.domain.Records
import com.callbackdev.thabit.domain.Regression
import com.callbackdev.thabit.domain.Regressions
import com.callbackdev.thabit.domain.StreakUnit
import com.callbackdev.thabit.domain.Streaks
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.format.TableAlign
import com.callbackdev.thabit.ui.format.TableCell
import com.callbackdev.thabit.ui.format.TableColumn
import com.callbackdev.thabit.ui.format.markdownTable
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * `stats.md` as a document — the coverage report, worked out as a value.
 *
 * The screen the CI metaphor unlocks (VISION §4.3), and the file where the app's
 * two promises about numbers are kept literally: **every rate travels with its
 * fraction**, and **every rule is printed at the bottom of the file** rather than
 * hidden in a method. A number nobody can recompute from their own export is not
 * allowed on this screen.
 *
 * English, like every other source file — the plain-language layer for `flaky`,
 * `coverage` and `regression` is the `README.md` tab (Fase 7), and the fraction
 * beside each percentage is the gloss that needs no translation at all.
 *
 * The hints are `<!-- -->` and not `#`, which is a **deviation from the mock in
 * VISION §4.3 and the reason for it is the series' own rule**: the comment
 * channel wears the host file's syntax (§1.1), and in markdown a `#` line is a
 * heading. Rendering a heading as if it were a comment is the same class of lie
 * that made the suite file `.test` instead of `.yaml`.
 */
data class StatsDocument(
    val today: LocalDate,
    /** Drawn cell by cell rather than as markdown: it is the one picture here. */
    val heatmap: HeatmapGrid,
    val coverage: CoverageReport,
    /** `| test | health | streak | 30d |`, padded — header, separator, rows. */
    val healthTable: List<String>,
    val flaky: List<String>,
    val regressions: List<String>,
    /**
     * The same three sections as facts rather than as rendered rows.
     *
     * They travel beside their markdown for the reason `tags` travels beside
     * `tagTable`: a table row read aloud is pipes and padding, so the screen
     * composes a sentence from the numbers instead — and it can only do that if
     * it still has the numbers (VISION §3.3.7, audited in Fase 13).
     */
    val healthRows: List<HealthRow>,
    val flakyRows: List<FlakyTest>,
    val regressionRows: List<Regression>,
    /** The tag table's lines, and the commits its rows point at. */
    val tagTable: List<String>,
    val tags: List<TagRow>,
    /** True when the suite has nothing to report on yet — the grid still draws. */
    val isEmpty: Boolean
) {
    /** `24 of 30 days ran · 6 days no run`. */
    val coverageLine: String
        get() = buildString {
            append(coverage.ratio).append(" days ran")
            if (coverage.noRunDays > 0) append(" · ").append(coverage.noRunDays).append(" days no run")
        }

    /** `▓▓▓▓▓▓▓▓░░ 80%` — the bar the whole app draws its rates with. */
    val coverageBar: String
        get() = "${CodeFormat.bar(coverage.fraction)} ${CodeFormat.percent(coverage.fraction)}"

    companion object {
        const val FILE_NAME: String = "stats.md"

        const val H_CONTRIBUTIONS: String = "## contributions (last ${Heatmap.WEEKS} weeks)"
        const val H_COVERAGE: String = "## coverage"
        const val H_HEALTH: String = "## suite health"
        const val H_FLAKY: String = "## flaky tests"
        const val H_REGRESSIONS: String = "## regressions"
        const val H_TAGS: String = "## tags"

        /** The window every windowed rate on this screen is computed over. */
        const val WINDOW_DAYS: Long = 30

        /**
         * The hints: one line each, factual, and the same words every time.
         *
         * Sentences, so string ids and not literals (Fase 15) — these are the
         * lines that say what `coverage`, `flaky` and `regression` mean, and a
         * gloss the reader cannot read glosses nothing. The formulas printed
         * beside them are formulas and do not move.
         */
        @StringRes val COVERAGE_HINT: Int = R.string.stats_hint_coverage

        @StringRes val FLAKY_HINT: Int = R.string.stats_hint_flaky

        @StringRes val REGRESSION_HINT: Int = R.string.stats_hint_regression

        /** The empty file, which still draws its grid — the shape of what is coming. */
        @StringRes val EMPTY_HINT: Int = R.string.stats_hint_empty

        /**
         * The rules, printed in the file.
         *
         * VISION §5 allows a statistic on screen only if the user can recompute
         * it from their own export. That promise is worth nothing if the
         * constants live only in the source, so the file states them where the
         * numbers are.
         *
         * They are **sentences**, not formulas — "pass rate over the last 30 days
         * below 60%" is a rule described in words — so since Fase 15 the screen
         * says them in the reader's language, while the canonical English
         * ([Health.FORMULA], [FlakyTests.RULE], [Regressions.RULE]) is what goes
         * into the export: a statement whose meaning moved with the phone's
         * language would not be an archive. The numbers come from the same
         * constants on both sides, so the two statements cannot drift — there is
         * a test that holds them together.
         */
        fun rules(): List<Rule> = listOf(
            Rule("health", R.string.stats_rule_health, listOf(Health.HALF_LIFE_UNITS)),
            Rule(
                "flaky",
                R.string.stats_rule_flaky,
                listOf(
                    FlakyTests.WINDOW_DAYS.toInt(),
                    (FlakyTests.THRESHOLD * 100).toInt(),
                    FlakyTests.MIN_SAMPLES
                )
            ),
            Rule(
                "regression",
                R.string.stats_rule_regression,
                listOf(
                    Regressions.MIN_RECENT_FAILS,
                    Regressions.RECENT_UNITS,
                    Regressions.MIN_GREEN_RUN
                )
            )
        )

        /**
         * The line above them, which is the only one of the four that is a
         * sentence rather than a formula: where the numbers were worked out, and
         * over how long. `window:` stays a key.
         */
        @StringRes val WINDOW_RULE: Int = R.string.stats_rule_window

        /**
         * One printed rule: the key the file shows it under, the sentence that
         * states it, and the numbers that sentence is about.
         *
         * The key is code and never moves; the numbers are the domain's own
         * constants, handed over rather than re-typed on the way to the screen.
         */
        data class Rule(val key: String, @StringRes val id: Int, val args: List<Any>)

        fun of(
            history: SuiteHistory,
            today: LocalDate,
            weekStartsOn: DayOfWeek = DayOfWeek.MONDAY
        ): StatsDocument {
            val from = today.minusDays(WINDOW_DAYS)
            val live = history.habits.filter { it.archivedAt == null }.sortedBy { it.position }

            val healthRows = live
                .map { habit ->
                    val health = Health.of(history, habit, today)
                    val streak = Streaks.current(history, habit, today)
                    val units = Outcomes.graded(history, habit, from, today, today)
                    HealthRow(
                        name = habit.name,
                        health = health,
                        streak = streak.length,
                        streakUnit = streak.unit,
                        passed = units.count { it.passed },
                        graded = units.size
                    )
                }
                // Health leads the table, and null health sinks: a test the app
                // knows nothing about yet has not earned a place at the top.
                .sortedWith(compareByDescending<HealthRow> { it.health ?: -1.0 }.thenBy { it.name })

            val regressions = Regressions.detect(history, today)
            val flaky = FlakyTests.detect(history, today, regressions)
            val tags = tagRows(history, today)

            return StatsDocument(
                today = today,
                heatmap = Heatmap.build(history, today, weekStartsOn),
                coverage = Coverage.lastDays(history, WINDOW_DAYS.toInt(), today),
                healthTable = healthTable(healthRows),
                flaky = flaky.map { test ->
                    "${test.habit.name} — ${CodeFormat.percent(test.passRate)} pass rate " +
                        "over $WINDOW_DAYS days (${test.fraction})"
                },
                regressions = regressions.map { regression ->
                    val unit = if (regression.unit == StreakUnit.WEEKS) "weeks" else "days"
                    "${regression.habit.name} — ${regression.greenRun} $unit green, " +
                        "${regression.recentFails} of the last ${regression.recentWindow} red"
                },
                healthRows = healthRows,
                flakyRows = flaky,
                regressionRows = regressions,
                tagTable = tagTable(tags),
                tags = tags,
                isEmpty = healthRows.isEmpty()
            )
        }

        private fun healthTable(rows: List<HealthRow>): List<String> {
            if (rows.isEmpty()) return emptyList()
            return markdownTable(
                columns = listOf(
                    TableColumn("test"),
                    TableColumn("health", TableAlign.RIGHT),
                    TableColumn("streak", TableAlign.RIGHT),
                    TableColumn("${WINDOW_DAYS}d", TableAlign.RIGHT)
                ),
                rows = rows.map { row ->
                    listOf(
                        TableCell(row.name),
                        TableCell(CodeFormat.percent(row.health)),
                        TableCell(row.streakText),
                        TableCell("${row.passed}/${row.graded}")
                    )
                }
            )
        }

        /**
         * The two records, as tag rows pointing at the commit that earned them.
         *
         * There are exactly two and neither is a score (VISION §6.4). A row with
         * no commit behind it is not listed at all — a link that goes nowhere is
         * worse than no link.
         */
        private fun tagRows(history: SuiteHistory, today: LocalDate): List<TagRow> = buildList {
            Records.longestStreak(history, today)?.let { record ->
                val end = record.streak.to
                if (end != null && record.streak.length > 1) {
                    val unit = if (record.streak.unit == StreakUnit.WEEKS) "weeks" else "days"
                    add(
                        TagRow(
                            tag = "longest-streak",
                            value = "${record.habit.name} · ${record.streak.length} $unit",
                            date = end
                        )
                    )
                }
            }
            val start = history.suiteStart()
            if (start != null) {
                Records.perfectWeeks(history, start, today.minusDays(1), today)
                    .forEach { week ->
                        add(
                            TagRow(
                                tag = "perfect-week",
                                value = "${week.week} · ${week.graded} tests",
                                date = minOf(week.week.endInclusive, today.minusDays(1))
                            )
                        )
                    }
            }
        }

        private fun tagTable(tags: List<TagRow>): List<String> {
            if (tags.isEmpty()) return emptyList()
            return markdownTable(
                columns = listOf(TableColumn("tag"), TableColumn("value"), TableColumn("date")),
                rows = tags.map { tag ->
                    listOf(
                        TableCell(tag.tag),
                        TableCell(tag.value),
                        TableCell(CodeFormat.date(tag.date))
                    )
                }
            )
        }
    }
}

/** One line of the suite-health table. */
data class HealthRow(
    val name: String,
    /** 0..1, or null when the app has nothing to go on — never a fake zero. */
    val health: Double?,
    val streak: Int,
    val streakUnit: StreakUnit,
    val passed: Int,
    val graded: Int
) {
    /**
     * `18`, or `6w` for a quota test.
     *
     * A quota's streak counts weeks, and a bare `6` in the same column as a
     * daily test's `18` would be two different units wearing one number.
     */
    val streakText: String
        get() = if (streakUnit == StreakUnit.WEEKS) "${streak}w" else streak.toString()
}

/** One row of the tag table, and the commit it links to. */
data class TagRow(val tag: String, val value: String, val date: LocalDate)
