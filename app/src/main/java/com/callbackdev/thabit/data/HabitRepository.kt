package com.callbackdev.thabit.data

import com.callbackdev.thabit.data.db.CheckDao
import com.callbackdev.thabit.data.db.DayDao
import com.callbackdev.thabit.data.db.DayEntity
import com.callbackdev.thabit.data.db.HabitDao
import com.callbackdev.thabit.data.db.toDomain
import com.callbackdev.thabit.data.db.toEntity
import com.callbackdev.thabit.domain.AmendWindow
import com.callbackdev.thabit.domain.DayBoundary
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * What a write attempt did.
 *
 * The read-only answer is a first-class result rather than an exception: history
 * older than the amend window is not an error condition, it is the app working
 * as designed (VISION §3.3.5), and the screens say so in words.
 */
enum class WriteOutcome {
    WRITTEN,

    /** Older than the amend window: history, forever. */
    READ_ONLY_DAY,

    /** The test does not exist, or had already left the suite that day. */
    UNKNOWN_TEST
}

/**
 * The only door to the data — and the only place in the app that writes.
 *
 * Two rules are enforced here rather than trusted to the callers:
 *
 * 1. **Nothing is written outside the amend window.** Today and the previous
 *    logical day, and that is all ([AmendWindow]). A caller cannot backfill last
 *    month by passing a date.
 * 2. **Presence is written only by [markPresent]**, which the deliberate
 *    interactions call — app open, widget tap, notification action. No read
 *    path, no repaint and no worker reaches it (VISION §7, §6.8).
 *
 * The clock is injected so DST, timezone moves and boundary edits are ordinary
 * tests instead of things one hopes about.
 */
class HabitRepository(
    private val habitDao: HabitDao,
    private val checkDao: CheckDao,
    private val dayDao: DayDao,
    private val settings: SettingsStore,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    /** The current logical day, per the `day_ends` setting. */
    suspend fun today(): LocalDate = boundary().logicalDate(clock.instant(), clock.zone)

    suspend fun boundary(): DayBoundary = settings.settings.first().boundary

    /** Wall-clock time of the tap — the `# 07:12` stamp, display detail only. */
    private fun now(): LocalTime = LocalTime.now(clock).withSecond(0).withNano(0)

    // ---- presence -------------------------------------------------------

    /**
     * Records that the user was deliberately here today.
     *
     * Idempotent by the table's own IGNORE conflict rule, so `first_seen` keeps
     * the first interaction of the day and a second app open cannot rewrite when
     * somebody showed up. Returns the logical day it stamped.
     */
    suspend fun markPresent(): LocalDate {
        val today = today()
        dayDao.markPresent(DayEntity(today.toString(), clock.millis()))
        return today
    }

    // ---- suite ----------------------------------------------------------

    fun observeSuite(): Flow<List<Habit>> =
        habitDao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    fun observeLiveSuite(): Flow<List<Habit>> =
        habitDao.observeLive().map { rows -> rows.mapNotNull { it.toDomain() } }

    /** New tests go to the end of the file (VISION §4.5: no drag-and-drop, ever). */
    suspend fun addHabit(
        name: String,
        type: HabitType = HabitType.BOOLEAN,
        assert: AssertSpec? = null,
        schedule: Schedule = Schedule.Daily,
        remindAt: LocalTime? = null,
        emoji: String? = null
    ): Long {
        val habit = Habit(
            name = name.trim(),
            type = type,
            assert = assert.takeIf { type == HabitType.COUNTER },
            schedule = schedule,
            remindAt = remindAt,
            emoji = emoji,
            position = habitDao.nextPosition(),
            createdAt = today()
        )
        return habitDao.insert(habit.toEntity())
    }

    /**
     * Edits apply from today on; the history keeps the rules of its time
     * (VISION §4.5). Nothing already written is re-graded by an edit.
     */
    suspend fun updateHabit(habit: Habit) = habitDao.update(habit.toEntity())

    /** `[rm]`: archive, never delete. Past runs stay in every committed day. */
    suspend fun archiveHabit(habitId: Long) = habitDao.archive(habitId, today().toString())

    // ---- checks ---------------------------------------------------------

    /** Boolean and avoid tests: mark the test passed on [date]. */
    suspend fun pass(habitId: Long, date: LocalDate): WriteOutcome =
        write(habitId, date) { Check(habitId, date, CheckState.PASS, at = now()) }

    /** An avoid test the user broke, or a test they declare failed. */
    suspend fun fail(habitId: Long, date: LocalDate, note: String? = null): WriteOutcome =
        write(habitId, date) { Check(habitId, date, CheckState.FAIL, note = note, at = now()) }

    /**
     * A counter's value.
     *
     * The verdict is the assert's, not the caller's: at or above the target the
     * row is a pass, below it the row is [CheckState.PROGRESS] — a number the
     * user typed, which is pending while the day is open and a fail once it
     * closes. Storing the verdict rather than recomputing it later is what keeps
     * an edited target from re-grading days that were judged by the old one.
     */
    suspend fun record(habitId: Long, date: LocalDate, value: Double): WriteOutcome =
        write(habitId, date) { habit ->
            val holds = habit.assert?.holds(value) ?: (value > 0)
            Check(
                habitId = habitId,
                date = date,
                state = if (holds) CheckState.PASS else CheckState.PROGRESS,
                value = value,
                at = now()
            )
        }

    /** `[+1]`: add one step to a counter (the common case must stay one tap). */
    suspend fun increment(habitId: Long, date: LocalDate, times: Int = 1): WriteOutcome {
        val habit = habitDao.byId(habitId)?.toDomain() ?: return WriteOutcome.UNKNOWN_TEST
        val step = habit.assert?.step ?: 1.0
        val current = checkDao.find(habitId, date.toString())?.toDomain()?.value ?: 0.0
        return record(habitId, date, current + step * times)
    }

    /**
     * `[~ skip]`, optionally until a later day.
     *
     * The window is stored as **one** row on the day it was tapped: one
     * interaction, one fact. Expanding it into a row per covered day would claim
     * the user was present on days that have not happened yet, and would make
     * those days count as coverage (see [SuiteHistory]).
     */
    suspend fun skip(
        habitId: Long,
        date: LocalDate,
        note: String? = null,
        until: LocalDate? = null
    ): WriteOutcome = write(habitId, date) {
        Check(
            habitId = habitId,
            date = date,
            state = CheckState.SKIP,
            note = note,
            at = now(),
            until = until?.takeIf { it > date }
        )
    }

    /** Undo: today is the working tree and it is *supposed* to change. */
    suspend fun clear(habitId: Long, date: LocalDate): WriteOutcome {
        if (!writable(date)) return WriteOutcome.READ_ONLY_DAY
        checkDao.delete(habitId, date.toString())
        return WriteOutcome.WRITTEN
    }

    private suspend fun writable(date: LocalDate): Boolean =
        AmendWindow(boundary()).isWritable(date, clock.instant(), clock.zone)

    private suspend fun write(
        habitId: Long,
        date: LocalDate,
        build: (Habit) -> Check
    ): WriteOutcome {
        if (!writable(date)) return WriteOutcome.READ_ONLY_DAY
        val habit = habitDao.byId(habitId)?.toDomain() ?: return WriteOutcome.UNKNOWN_TEST
        if (!habit.isActiveOn(date)) return WriteOutcome.UNKNOWN_TEST
        checkDao.upsert(build(habit).toEntity())
        return WriteOutcome.WRITTEN
    }

    // ---- history --------------------------------------------------------

    /**
     * The read model for a date range — everything the pure engines need.
     *
     * Skip windows opened before the range are pulled in on purpose: a week away
     * declared last Friday must still read as skipped on this week's Monday,
     * and a plain range query would have quietly turned those days back into
     * pending ones.
     */
    fun observeHistory(from: LocalDate, to: LocalDate): Flow<SuiteHistory> = combine(
        habitDao.observeAll(),
        checkDao.observeBetween(from.toString(), to.toString()),
        checkDao.observeOpenSkipWindows(from.toString()),
        dayDao.observeBetween(from.toString(), to.toString())
    ) { habits, checks, windows, days ->
        SuiteHistory(
            habits = habits.mapNotNull { it.toDomain() },
            checks = (checks + windows).distinct().mapNotNull { it.toDomain() },
            presentDays = days.mapNotNull { it.toDomain()?.date }.toSet()
        )
    }

    suspend fun history(from: LocalDate, to: LocalDate): SuiteHistory {
        val rows = checkDao.between(from.toString(), to.toString()) +
            checkDao.openSkipWindows(from.toString())
        return SuiteHistory(
            habits = habitDao.all().mapNotNull { it.toDomain() },
            checks = rows.distinct().mapNotNull { it.toDomain() },
            presentDays = dayDao.between(from.toString(), to.toString())
                .mapNotNull { it.toDomain()?.date }
                .toSet()
        )
    }

    /**
     * Everything, observed — what the screens read.
     *
     * Deliberately not a window. Streaks and health run back to a test's
     * `createdAt`, so a range query would have to guess how far back is far
     * enough and would silently truncate the longest streak in the suite the day
     * it grew past the guess. A decade of checks is a few hundred kilobytes
     * (VISION §7), so the honest query is the whole table.
     */
    fun observeFullHistory(): Flow<SuiteHistory> = combine(
        habitDao.observeAll(),
        checkDao.observeAll(),
        dayDao.observeAll()
    ) { habits, checks, days ->
        SuiteHistory(
            habits = habits.mapNotNull { it.toDomain() },
            checks = checks.mapNotNull { it.toDomain() },
            presentDays = days.mapNotNull { it.toDomain()?.date }.toSet()
        )
    }

    /** Everything, for the stats screens and the export. It is a small database. */
    suspend fun fullHistory(): SuiteHistory = SuiteHistory(
        habits = habitDao.all().mapNotNull { it.toDomain() },
        checks = checkDao.all().mapNotNull { it.toDomain() },
        presentDays = dayDao.all().mapNotNull { it.toDomain()?.date }.toSet()
    )
}
