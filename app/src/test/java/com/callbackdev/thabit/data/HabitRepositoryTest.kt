package com.callbackdev.thabit.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The only door to the data, exercised against a real Room database.
 *
 * The two invariants under test are the ones a caller must not be able to break:
 * nothing is written outside the amend window, and presence is written once a
 * day by a deliberate interaction and by nothing else.
 */
@RunWith(RobolectricTestRunner::class)
class HabitRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private lateinit var db: ThabitDatabase
    private lateinit var settings: SettingsStore
    private var now: Instant = Instant.parse("2026-08-21T10:00:00Z")

    private val clock: Clock = object : Clock() {
        override fun getZone(): ZoneId = rome
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        db = ThabitDatabase.inMemory(ApplicationProvider.getApplicationContext())
        settings = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun travelTo(instant: String) { now = Instant.parse(instant) }

    /** Adds a test as if it had been created two days ago, then returns to now. */
    private suspend fun habitCreatedEarlier(name: String): Long {
        val here = now
        travelTo("2026-08-19T10:00:00Z")
        val id = repository.addHabit(name)
        now = here
        return id
    }

    // ---- presence --------------------------------------------------------

    @Test
    fun `presence is written once per logical day and keeps the first sighting`() = runTest {
        val first = repository.markPresent()
        val firstSeen = db.dayDao().between(first.toString(), first.toString()).single().firstSeen

        travelTo("2026-08-21T18:00:00Z")
        repository.markPresent()
        val rows = db.dayDao().all()
        assertEquals(1, rows.size)
        assertEquals(firstSeen, rows.single().firstSeen)
    }

    @Test
    fun `a new logical day gets its own presence row`() = runTest {
        repository.markPresent()
        travelTo("2026-08-22T09:00:00Z")
        repository.markPresent()
        assertEquals(2, db.dayDao().all().size)
    }

    @Test
    fun `a tap just after midnight with a late day_ends lands on yesterday`() = runTest {
        settings.setDayEnds(LocalTime.of(3, 0))
        travelTo("2026-08-21T23:30:00Z") // 01:30 Rome on the 22nd
        val stamped = repository.markPresent()
        assertEquals(LocalDate.of(2026, 8, 21), stamped)
        assertEquals("2026-08-21", db.dayDao().all().single().date)
    }

    @Test
    fun `nothing but markPresent writes a day row`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        repository.pass(id, repository.today())
        repository.history(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        // Adding a test and checking it off wrote checks, never presence: only
        // the deliberate interactions call markPresent (app open, widget, notification).
        assertTrue(db.dayDao().all().isEmpty())
    }

    // ---- the amend window ------------------------------------------------

    @Test
    fun `today and yesterday are writable`() = runTest {
        val id = habitCreatedEarlier("meditate 10 min")
        val today = repository.today()
        assertEquals(WriteOutcome.WRITTEN, repository.pass(id, today))
        assertEquals(WriteOutcome.WRITTEN, repository.pass(id, today.minusDays(1)))
    }

    @Test
    fun `a test added today cannot be amended into yesterday - it did not exist`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        // The grace window is open on yesterday, but the suite of yesterday did
        // not contain this test, and backdating it would invent a run.
        assertEquals(WriteOutcome.UNKNOWN_TEST, repository.pass(id, repository.today().minusDays(1)))
    }

    @Test
    fun `two days back is history and refuses the write`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        assertEquals(WriteOutcome.READ_ONLY_DAY, repository.pass(id, today.minusDays(2)))
        assertEquals(WriteOutcome.READ_ONLY_DAY, repository.skip(id, today.minusDays(30)))
        assertEquals(WriteOutcome.READ_ONLY_DAY, repository.clear(id, today.minusDays(2)))
        assertNull(db.checkDao().find(id, today.minusDays(2).toString()))
    }

    @Test
    fun `the future is not writable either`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        assertEquals(WriteOutcome.READ_ONLY_DAY, repository.pass(id, repository.today().plusDays(1)))
    }

    @Test
    fun `an unknown test cannot be checked off`() = runTest {
        assertEquals(WriteOutcome.UNKNOWN_TEST, repository.pass(404L, repository.today()))
    }

    // ---- checks ----------------------------------------------------------

    @Test
    fun `a pass carries the time it was typed`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        repository.pass(id, today)
        val row = db.checkDao().find(id, today.toString())!!
        assertEquals(CheckState.PASS.name, row.state)
        assertNotNull(row.at)
    }

    @Test
    fun `undo removes the row - today is the working tree`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        repository.pass(id, today)
        assertEquals(WriteOutcome.WRITTEN, repository.clear(id, today))
        assertNull(db.checkDao().find(id, today.toString()))
    }

    @Test
    fun `a second tap replaces the first instead of stacking opinions`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        repository.pass(id, today)
        repository.skip(id, today, note = "rest day")
        val rows = db.checkDao().between(today.toString(), today.toString())
        assertEquals(1, rows.size)
        assertEquals(CheckState.SKIP.name, rows.single().state)
    }

    @Test
    fun `a counter below its target is progress, not a failure`() = runTest {
        val id = repository.addHabit(
            "pushups", HabitType.COUNTER, AssertSpec(30.0, "reps", 10.0)
        )
        val today = repository.today()
        repository.record(id, today, 12.0)
        val row = db.checkDao().find(id, today.toString())!!
        assertEquals(CheckState.PROGRESS.name, row.state)
        assertEquals(12.0, row.value!!, 0.0)
    }

    @Test
    fun `a counter reaching its target passes`() = runTest {
        val id = repository.addHabit("pushups", HabitType.COUNTER, AssertSpec(30.0, "reps"))
        val today = repository.today()
        repository.record(id, today, 30.0)
        assertEquals(CheckState.PASS.name, db.checkDao().find(id, today.toString())!!.state)
    }

    @Test
    fun `plus one adds a step and flips the box when the assert holds`() = runTest {
        val id = repository.addHabit(
            "water", HabitType.COUNTER, AssertSpec(3.0, "glasses", 1.0)
        )
        val today = repository.today()
        repository.increment(id, today)
        repository.increment(id, today)
        assertEquals(CheckState.PROGRESS.name, db.checkDao().find(id, today.toString())!!.state)
        repository.increment(id, today)
        val row = db.checkDao().find(id, today.toString())!!
        assertEquals(CheckState.PASS.name, row.state)
        assertEquals(3.0, row.value!!, 0.0)
    }

    // ---- skip windows ----------------------------------------------------

    @Test
    fun `a week away is one row, and the days it covers read as skipped`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        repository.skip(id, today, note = "away", until = today.plusDays(6))

        assertEquals(1, db.checkDao().all().size)
        val history = repository.history(today, today.plusDays(6))
        (0L..6L).forEach { offset ->
            val check = history.check(id, today.plusDays(offset))
            assertEquals(CheckState.SKIP, check?.state)
        }
    }

    @Test
    fun `unskip takes back the rest of a window and keeps the days it already covered`() = runTest {
        val id = habitCreatedEarlier("meditate 10 min")
        travelTo("2026-08-19T10:00:00Z")
        val monday = repository.today()
        repository.skip(id, monday, note = "away", until = monday.plusDays(6))

        // Home two days early.
        travelTo("2026-08-21T10:00:00Z")
        val wednesday = repository.today()
        assertEquals(WriteOutcome.WRITTEN, repository.resumeSkip(id, wednesday))

        val history = repository.history(monday, monday.plusDays(6))
        // The days already covered are closed history and stay skipped...
        assertEquals(CheckState.SKIP, history.check(id, monday)?.state)
        assertEquals(CheckState.SKIP, history.check(id, monday.plusDays(1))?.state)
        // ...and from today on the test is due again.
        assertNull(history.check(id, wednesday))
        assertNull(history.check(id, wednesday.plusDays(1)))
    }

    @Test
    fun `unskip on the day the skip was tapped removes the row entirely`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        repository.skip(id, today, note = "rest day", until = today.plusDays(6))

        assertEquals(WriteOutcome.WRITTEN, repository.resumeSkip(id, today))
        assertEquals(0, db.checkDao().all().size)
    }

    @Test
    fun `unskip finds nothing to undo without complaining`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        assertEquals(WriteOutcome.WRITTEN, repository.resumeSkip(id, repository.today()))
        assertEquals(WriteOutcome.UNKNOWN_TEST, repository.resumeSkip(404L, repository.today()))
    }

    @Test
    fun `the days a skip window covers are not days the app saw`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val today = repository.today()
        repository.markPresent()
        repository.skip(id, today, note = "away", until = today.plusDays(6))

        val history = repository.history(today, today.plusDays(6))
        assertTrue(history.ran(today))
        // A skip declared in advance is not evidence that anybody was there.
        assertFalse(history.ran(today.plusDays(3)))
    }

    @Test
    fun `a skip window opened before the range still reaches into it`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        val start = repository.today()
        repository.skip(id, start, note = "away", until = start.plusDays(6))

        val later = repository.history(start.plusDays(3), start.plusDays(6))
        assertEquals(CheckState.SKIP, later.check(id, start.plusDays(4))?.state)
    }

    // ---- the suite -------------------------------------------------------

    @Test
    fun `new tests go to the end of the file`() = runTest {
        val first = repository.addHabit("meditate 10 min")
        val second = repository.addHabit("journal")
        val suite = db.habitDao().all()
        assertEquals(listOf(first, second), suite.map { it.id })
        assertEquals(listOf(0, 1), suite.map { it.position })
    }

    @Test
    fun `archiving keeps the test and every run it ever had`() = runTest {
        val id = habitCreatedEarlier("meditate 10 min")
        val today = repository.today()
        repository.pass(id, today.minusDays(1))
        repository.archiveHabit(id)

        assertEquals(1, db.habitDao().all().size)
        assertEquals(today.toString(), db.habitDao().byId(id)!!.archivedAt)
        assertEquals(1, db.checkDao().all().size)

        // Gone from today's run, still in yesterday's.
        val history = repository.history(today.minusDays(1), today)
        assertTrue(Verdicts.outcomesOn(history, today, today).isEmpty())
        assertEquals(1, Verdicts.outcomesOn(history, today.minusDays(1), today).size)
    }

    @Test
    fun `a schedule survives the round trip through storage`() = runTest {
        val id = repository.addHabit(
            "run 5k", schedule = Schedule.Quota(3), emoji = "🏃"
        )
        val stored = db.habitDao().byId(id)!!
        assertEquals("3/week", stored.schedule)
        val loaded = repository.history(repository.today(), repository.today())
            .habits.single { it.id == id }
        assertEquals(Schedule.Quota(3), loaded.schedule)
        assertEquals("🏃", loaded.emoji)
    }

    @Test
    fun `changing day_ends relabels today, never the days already written`() = runTest {
        val id = habitCreatedEarlier("meditate 10 min")
        travelTo("2026-08-21T23:30:00Z") // 01:30 Rome on the 22nd
        assertEquals(LocalDate.of(2026, 8, 22), repository.today())
        repository.pass(id, repository.today())

        // The night owl moves the boundary at half past one in the morning.
        settings.setDayEnds(LocalTime.of(3, 0))

        // Today is now the 21st: the current day was relabelled, as it should be.
        assertEquals(LocalDate.of(2026, 8, 21), repository.today())
        // The row that was already written keeps its own date. The past is judged
        // by the rules of its time, and a setting cannot move a day that is over.
        assertEquals(
            listOf("2026-08-22"),
            db.checkDao().all().map { it.date }
        )
    }

    @Test
    fun `the boundary follows the setting`() = runTest {
        assertEquals(Duration.ZERO, Duration.between(LocalTime.MIDNIGHT, repository.boundary().dayEnds))
        settings.setDayEnds(LocalTime.of(3, 0))
        assertEquals(LocalTime.of(3, 0), repository.boundary().dayEnds)
    }

    // ---- what the first-run check reads (Fase 14) --------------------------

    @Test
    fun `a fresh install has never been used`() = runTest {
        assertFalse(repository.everUsed())
    }

    @Test
    fun `one test is enough to count as used`() = runTest {
        repository.addHabit("meditate 10 min")

        assertTrue(repository.everUsed())
    }

    /**
     * `[rm]` archives, it never deletes — and an archived test was still written
     * by somebody. An update must not greet them with a setup screen.
     */
    @Test
    fun `an archived test still counts`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        repository.archiveHabit(id)

        assertTrue(repository.everUsed())
    }

    /**
     * Opening the app is not using it: `markPresent` fires from `onStart`, one
     * step after the check runs, so a presence row that counted would let a
     * fresh install race itself past its own first run.
     */
    @Test
    fun `opening the app is not what makes an install used`() = runTest {
        repository.markPresent()

        assertFalse(repository.everUsed())
    }
}
