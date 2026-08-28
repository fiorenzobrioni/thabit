package com.callbackdev.thabit.notifications

import android.content.res.Resources
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.BuildResult
import com.callbackdev.thabit.domain.DayRun
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.log.LogEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The three notifications' content, pure and testable.
 *
 * The l10n split is the series' (tsteps' `StepsNotifications`, same shape), and
 * here it carries more weight than anywhere else, because a notification is the
 * one surface with **no file around it**: the reader gets a line of text on a
 * lock screen with nothing to look up. So:
 *
 * - the **title** is prose in the reader's language, and it always states the
 *   fact with its arithmetic — `~ 4 of 6 tests passed`, never a bare
 *   `build unstable` (VISION §3.3.7, §4.4);
 * - the **body** is the file's own channel, English, and it is the same commit
 *   the log will show. It decorates, it never carries a fact the title does not
 *   already have.
 *
 * Two shapes each, the sibling's device lesson: the collapsed shade gets one
 * compact line, the expanded shade gets the same facts one per line.
 */
object ThabitNotifications {

    data class Content(
        /** Chrome, localized, and the only place a verdict is *explained*. */
        val title: String,
        /** Collapsed shade: one terminal line, never wraps on `\n`. */
        val summary: String,
        /** Expanded shade (BigTextStyle): the same facts, one per line. */
        val expanded: String
    )

    /**
     * The day that just closed, as the log will show it.
     *
     * [commit] is the log's own entry for that day, so the shade and
     * `habits_history.diff` cannot drift into two spellings of one commit — the
     * hash, the message and the verdict line are literally the same strings.
     *
     * Only days that graded something are worth posting; the caller checks. A
     * day whose tests were all skipped has a commit and no verdict, and a
     * notification with nothing to say is a buzz, not information.
     */
    fun dailyCommit(run: DayRun, commit: LogEntry.Commit, resources: Resources): Content {
        val title = when (run.result) {
            BuildResult.PASSED -> resources.getQuantityString(
                R.plurals.notif_commit_passed, run.graded, run.graded
            )
            BuildResult.UNSTABLE -> resources.getString(
                R.string.notif_commit_unstable, run.passed, run.graded
            )
            else -> resources.getQuantityString(
                R.plurals.notif_commit_failed, run.graded, run.graded
            )
        }
        return Content(
            title = title,
            // The title already carries the arithmetic: the summary adds the
            // hash, which is the one thing it does not say.
            summary = "commit ${commit.hash} · ${commit.verdict?.text ?: commit.message}",
            expanded = buildString {
                appendLine(commit.headline)
                appendLine("Date: ${dayName(run.date)} ${run.date}")
                appendLine(commit.message)
                commit.verdict?.let { appendLine(it.text) }
                append("$ thabit log")
            }
        )
    }

    /**
     * The evening digest — one summary, never one nag per test (VISION §3.3.4).
     *
     * [pending] is what is still open on the logical day, in file order. Holding
     * avoid tests are **not** in it and that is the point: a test that passes
     * unless you break it is not something to remind anybody about.
     */
    fun pendingDigest(
        pending: List<TestOutcome>,
        dayEnds: LocalTime,
        resources: Resources
    ): Content = Content(
        title = resources.getQuantityString(
            R.plurals.notif_digest_title, pending.size, pending.size
        ),
        // The title says how many; the names are what the title cannot fit.
        summary = pending.joinToString(" · ") { it.habit.name },
        expanded = buildString {
            appendLine("$ thabit --status")
            pending.forEach { appendLine(testLine(it)) }
            // `pending` is a state name and stays; the sentence around it is
            // the reader's (Fase 15), like the rest of a notification.
            append(
                "# " + resources.getString(
                    R.string.notif_digest_footer, pending.size, CodeFormat.time(dayEnds)
                )
            )
        }
    )

    /**
     * One test's reminder: the nudge itself.
     *
     * The title is the test's **name**, which is user data and therefore already
     * in the user's own words — the one title in the app that needs no
     * translation because the user wrote it. The plain-language half is the
     * body, which says what state the test is in right now.
     */
    fun reminder(outcome: TestOutcome, remindAt: LocalTime, resources: Resources): Content {
        val habit = outcome.habit
        val name = listOfNotNull(habit.emoji, habit.name).joinToString(" ")
        val counter = habit.assert
        val summary = when {
            habit.type == HabitType.COUNTER && counter != null -> resources.getString(
                R.string.cd_detail_counter,
                CodeFormat.number(outcome.value ?: 0.0),
                CodeFormat.number(counter.target),
                counter.unit
            )
            // An avoid test is holding, and the body says exactly that: the
            // reminder is the intention arriving on time, not a chore.
            outcome.state == TestState.HOLDING -> resources.getString(R.string.cd_state_holding)
            else -> resources.getString(R.string.cd_state_pending)
        }
        return Content(
            title = name,
            summary = summary,
            expanded = buildString {
                appendLine(testLine(outcome))
                appendLine("  when: ${habit.schedule.format()}")
                appendLine("  remind: \"${CodeFormat.time(remindAt)}\"")
                append("# " + resources.getString(R.string.notif_reminder_footer))
            }
        )
    }

    /** `- [ ] read 20 pages  # 12/20 pages` — the suite's own line, verbatim. */
    private fun testLine(outcome: TestOutcome): String = buildString {
        // The two glyphs an open day can wear. `[·]` is the one nobody has met
        // anywhere else, so it never travels alone: the body's plain sentence
        // above it says what holding means (VISION §3.3.7).
        append(if (outcome.state == TestState.HOLDING) "- [·] " else "- [ ] ")
        outcome.habit.emoji?.let { append(it).append(" ") }
        append(outcome.habit.name)
        val counter = outcome.habit.assert
        if (counter != null) {
            append("  # ")
            append(CodeFormat.fraction(outcome.value ?: 0.0, counter.target))
            append(" ").append(counter.unit)
        }
    }

    /** `Fri` — the log's own day name, English like every other comment. */
    private fun dayName(date: LocalDate): String = DAY_NAME.format(date)

    private val DAY_NAME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
}
