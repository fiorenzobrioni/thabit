package com.callbackdev.thabit.data.db

import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.DayPresence
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Storage rows ↔ domain objects.
 *
 * The mapping is deliberately total and lenient in one direction only: a row the
 * app cannot read (a schedule string from a future version, an unknown state) is
 * *dropped*, never guessed. Guessing would put a value in the file that nobody
 * typed, which is the one thing this app does not do — and dropping is
 * recoverable, because the row is still in the database and in the export.
 */
internal val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun HabitEntity.toDomain(): Habit? {
    val habitType = runCatching { HabitType.valueOf(type) }.getOrNull() ?: return null
    val parsedSchedule = Schedule.parse(schedule) ?: return null
    val created = runCatching { LocalDate.parse(createdAt) }.getOrNull() ?: return null
    val target = assertTarget
    val assertSpec = if (habitType == HabitType.COUNTER && target != null) {
        runCatching { AssertSpec(target, assertUnit.orEmpty(), assertStep ?: 1.0) }.getOrNull()
    } else {
        null
    }
    return Habit(
        id = id,
        name = name,
        type = habitType,
        assert = assertSpec,
        schedule = parsedSchedule,
        remindAt = remindAt?.let { runCatching { LocalTime.parse(it, HHMM) }.getOrNull() },
        emoji = emoji,
        position = position,
        createdAt = created,
        archivedAt = archivedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )
}

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    type = type.name,
    assertTarget = assert?.target,
    assertUnit = assert?.unit,
    assertStep = assert?.step,
    schedule = schedule.format(),
    remindAt = remindAt?.format(HHMM),
    emoji = emoji,
    position = position,
    createdAt = createdAt.toString(),
    archivedAt = archivedAt?.toString()
)

fun CheckEntity.toDomain(): Check? {
    val parsedState = CheckState.parse(state) ?: return null
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return Check(
        habitId = habitId,
        date = parsedDate,
        state = parsedState,
        value = value,
        note = note,
        at = at?.let { runCatching { LocalTime.parse(it, HHMM) }.getOrNull() },
        until = until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )
}

fun Check.toEntity(): CheckEntity = CheckEntity(
    habitId = habitId,
    date = date.toString(),
    state = state.name,
    value = value,
    note = note,
    at = at?.format(HHMM),
    until = until?.toString()
)

fun DayEntity.toDomain(): DayPresence? {
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    return DayPresence(parsedDate, firstSeen)
}
