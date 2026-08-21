package com.callbackdev.thabit.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One test's result on one logical day — the only thing the app ever writes
 * about a run, and the fact every verdict is derived from (VISION §6.8).
 *
 * There is exactly one row per test per logical day. It exists because the user
 * tapped something: an untouched test has no row at all, which is what lets the
 * app tell "still to do" from "deliberately skipped" without inventing either.
 *
 * [at] is the wall-clock time stamped on the tap — the `# 07:12` in the file.
 * It is display detail only: the logical [date] is what everything is keyed on,
 * so a tap at 01:00 with `day_ends: 03:00` lands on yesterday's run and stays
 * there even if `day_ends` changes later (the past is never relabelled).
 */
data class Check(
    val habitId: Long,
    val date: LocalDate,
    val state: CheckState,
    /** The counter's value; null for boolean and avoid tests. */
    val value: Double? = null,
    /** The optional note on a skip or a failed avoid test. */
    val note: String? = null,
    /** Wall-clock time of the tap that wrote this row. */
    val at: LocalTime? = null,
    /**
     * Skips only: the last logical day this skip covers, so a week away is one
     * interaction instead of one skip per test per day (VISION §4.1).
     *
     * The covered days are **not** materialised: this row is the only one
     * written, and the days between [date] and [until] are expanded on read by
     * [com.callbackdev.thabit.domain.SuiteHistory]. A row per covered day would
     * claim the user was present on days that have not happened yet, and those
     * rows would make those days count as coverage — a week away would come back
     * as a week of full attendance.
     *
     * Downstream nothing knows the difference: every denominator and the export
     * see plain skips, because that is what the read model hands them.
     */
    val until: LocalDate? = null
) {
    /** True when this row is a skip that opened a window wider than its own day. */
    val isSkipWindow: Boolean get() = state == CheckState.SKIP && until != null && until > date
}

/**
 * What the user said about a test on a day.
 *
 * *Pending* is deliberately absent: a pending test is the absence of a row,
 * because writing "not done yet" every morning for every test would make the
 * file assert something nobody typed — and would make a `no run` day
 * indistinguishable from a day someone showed up for.
 *
 * [PROGRESS] is not a verdict but a fact: a counter whose value was logged and
 * whose assert did not hold yet. It exists so `12/30` at nine in the morning is
 * neither a pass nor a failure — the day is still open, the user typed a real
 * number, and the row says exactly that. It resolves to a fail at the boundary
 * like any unfinished test, and reads as pending while the day is open.
 */
enum class CheckState {
    PASS,
    FAIL,
    SKIP,
    PROGRESS;

    companion object {
        /** Canonical spelling — the file's, the database's and the export's. */
        fun parse(raw: String): CheckState? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

/**
 * Presence: the app was deliberately used on this logical day.
 *
 * Written by the first deliberate interaction only — opening the app, tapping
 * the widget, tapping a notification action (VISION §7). Never by a widget
 * repaint, never by the rollover worker: an automatic event stamping presence
 * would invent a user who was not there, and the worker writing anything at all
 * would break §6.8.
 *
 * It is evidence, not a verdict. Its absence is the whole reason the app can say
 * `no run` instead of inventing a red build for a day nobody was there.
 */
data class DayPresence(
    val date: LocalDate,
    /** Wall-clock instant (epoch millis) of the first deliberate interaction. */
    val firstSeen: Long
)
