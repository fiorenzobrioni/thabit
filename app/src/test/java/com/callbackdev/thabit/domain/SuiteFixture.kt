package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate

/**
 * Builders for the pure tests: a suite, some checks, some days that ran.
 *
 * The whole of Fase 2 reads a [SuiteHistory] and nothing else, so a test is a
 * literal description of a user's month — which is exactly what these fixtures
 * write.
 */
internal object Fixture {

    val D0: LocalDate = LocalDate.of(2026, 8, 1)

    fun habit(
        id: Long = 1L,
        name: String = "meditate 10 min",
        type: HabitType = HabitType.BOOLEAN,
        schedule: Schedule = Schedule.Daily,
        createdAt: LocalDate = D0,
        archivedAt: LocalDate? = null,
        assert: AssertSpec? = null,
        position: Int = 0
    ) = Habit(
        id = id,
        name = name,
        type = type,
        assert = assert,
        schedule = schedule,
        position = position,
        createdAt = createdAt,
        archivedAt = archivedAt
    )

    fun pass(habitId: Long, date: LocalDate) = Check(habitId, date, CheckState.PASS)
    fun fail(habitId: Long, date: LocalDate) = Check(habitId, date, CheckState.FAIL)
    fun skip(habitId: Long, date: LocalDate, until: LocalDate? = null, note: String? = null) =
        Check(habitId, date, CheckState.SKIP, note = note, until = until)

    fun progress(habitId: Long, date: LocalDate, value: Double) =
        Check(habitId, date, CheckState.PROGRESS, value = value)

    /** Every day of the closed range, as days the app saw. */
    fun ranFrom(from: LocalDate, to: LocalDate): Set<LocalDate> =
        generateSequence(from) { d -> d.plusDays(1).takeIf { !it.isAfter(to) } }.toSet()

    fun history(
        habits: List<Habit>,
        checks: List<Check> = emptyList(),
        present: Set<LocalDate> = emptySet(),
        amended: Set<LocalDate> = emptySet()
    ) = SuiteHistory(habits, checks, present, amended)

    /** A daily test passed every day of the range, with the presence to match. */
    fun greenRun(habit: Habit, from: LocalDate, to: LocalDate): SuiteHistory {
        val days = generateSequence(from) { d -> d.plusDays(1).takeIf { !it.isAfter(to) } }.toList()
        return history(listOf(habit), days.map { pass(habit.id, it) }, days.toSet())
    }
}
