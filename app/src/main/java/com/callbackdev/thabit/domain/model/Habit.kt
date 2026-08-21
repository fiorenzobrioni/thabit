package com.callbackdev.thabit.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * One test of the suite.
 *
 * A habit is a *test*: it has a name, an assertion it makes about the day, a
 * schedule that says when it is due, and nothing else. There is no strength
 * counter, no streak field and no cached verdict on this class — those are
 * derived on read from the [Check] rows (VISION §6.8), so there is no state to
 * corrupt and `--amend` recomputes everything for free.
 *
 * [createdAt] is the anchor of every statistic about this test: a habit added
 * yesterday is `1/1`, never `1/30` (VISION §4.3). [archivedAt] is how a test
 * leaves the suite — `[rm]` archives, it never deletes: past runs stay in every
 * committed day and in the history (VISION §4.5).
 */
data class Habit(
    val id: Long = 0L,
    val name: String,
    val type: HabitType = HabitType.BOOLEAN,
    /** Only meaningful for [HabitType.COUNTER]; null for boolean and avoid tests. */
    val assert: AssertSpec? = null,
    val schedule: Schedule = Schedule.Daily,
    /** Inexact per-test reminder (Fase 9); null = no reminder. */
    val remindAt: LocalTime? = null,
    /** Optional emoji shown on the test line — the series' only per-item identity (VISION §6.6). */
    val emoji: String? = null,
    /** Position in the file; new tests go to the end of the suite. */
    val position: Int = 0,
    /** Logical date the test entered the suite. Every window clamps to it. */
    val createdAt: LocalDate,
    /** Logical date the test left the suite, or null while it is live. */
    val archivedAt: LocalDate? = null
) {
    /**
     * Whether this test belongs to the suite on [date].
     *
     * Archiving is exclusive: a test archived on D is already gone on D — it
     * leaves the suite as a `-` line in that day's diff, and the days before it
     * keep their runs untouched.
     */
    fun isActiveOn(date: LocalDate): Boolean =
        !date.isBefore(createdAt) && (archivedAt == null || date.isBefore(archivedAt))

    /** Calendar-level occurrence: does the schedule put this test on [date] at all? */
    fun occursOn(date: LocalDate): Boolean =
        isActiveOn(date) && schedule.occursOn(date, createdAt)
}

/**
 * The three kinds of assertion a test can make.
 *
 * [BOOLEAN] asserts an event happened; [COUNTER] asserts a measurable reached
 * its target (the field's "measurable habits", improved by the metaphor —
 * VISION §6.1); [AVOID] asserts an *absence*, so it holds by default and only
 * fails when the user says it did (VISION §4.1, §6.2).
 */
enum class HabitType { BOOLEAN, COUNTER, AVOID }

/**
 * A counter test's assertion: `pages >= 20`.
 *
 * Only `>=` exists, deliberately. An "at most" assertion is what an [HabitType.AVOID]
 * test is for, and two comparison operators would put a choice in front of the
 * user in the wizard's first sixty seconds to buy a case the type system already
 * covers (VISION §3.3.1).
 *
 * [step] is the size of the `[+1]` control on the test line — the small-step
 * counters (water glasses, pushup sets) whose common case must stay one tap.
 */
data class AssertSpec(
    val target: Double,
    val unit: String,
    val step: Double = 1.0
) {
    init {
        require(target > 0) { "assert target must be positive" }
        require(step > 0) { "assert step must be positive" }
    }

    /** The assertion itself. A counter passes when its value reaches the target. */
    fun holds(value: Double): Boolean = value >= target
}
