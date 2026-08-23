package com.callbackdev.thabit.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.NotificationStateStore
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The three things that decide whether anything is posted at all, against a real
 * database and a real DataStore.
 *
 * They are tested through their dependency-explicit cores rather than through a
 * `NotificationManager`, for the same reason the rest of the app is: what is
 * worth asserting is the **decision** — when the app speaks, when it stays quiet,
 * and that it never speaks twice about the same day.
 */
@RunWith(RobolectricTestRunner::class)
class NotifierWatchersTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** 21 August 2026, 09:00 in Rome: the closed day is the 20th. */
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-08-21T07:00:00Z"), ZoneId.of("Europe/Rome"))

    private val yesterday = LocalDate.of(2026, 8, 20)
    private val today = LocalDate.of(2026, 8, 21)

    private lateinit var db: ThabitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var state: NotificationStateStore
    private lateinit var repository: HabitRepository

    private val resources =
        ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = ThabitDatabase.inMemory(context)
        settings = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") },
            clock
        )
        state = NotificationStateStore(
            PreferenceDataStoreFactory.create { folder.newFile("notif.preferences_pb") }
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings, clock)
    }

    @After
    fun tearDown() = db.close()

    // ---- the daily commit -------------------------------------------------

    /** A test passed yesterday, on a day the app saw. */
    private suspend fun aCommittedYesterday(): Long {
        val id = repository.addHabit("meditate 10 min")
        // `addHabit` stamps today, and a test cannot have results before it
        // existed: back-date it so yesterday is a day it was live on.
        repository.updateHabit(repository.habit(id)!!.copy(createdAt = yesterday.minusDays(7)))
        db.dayDao().markPresent(
            com.callbackdev.thabit.data.db.DayEntity(yesterday.toString(), 0L)
        )
        db.checkDao().upsert(
            com.callbackdev.thabit.data.db.CheckEntity(
                habitId = id,
                date = yesterday.toString(),
                state = "PASS"
            )
        )
        return id
    }

    private suspend fun runCommit(canPost: Boolean = true): List<ThabitNotifications.Content> {
        val posted = mutableListOf<ThabitNotifications.Content>()
        DailyCommitNotifier.evaluate(
            repository = repository,
            settingsStore = settings,
            stateStore = state,
            resources = resources,
            canPost = { canPost },
            post = { posted += it }
        )
        return posted
    }

    @Test
    fun `the closed day is announced once, with its verdict`() = runTest {
        aCommittedYesterday()
        val first = runCommit()
        assertEquals(1, first.size)
        assertTrue(first.single().title.contains("passed"))
        assertEquals(yesterday, state.committedDay())

        // A worker that ran twice, an alarm the OS batched: the same day never
        // gets a second notification.
        assertTrue(runCommit().isEmpty())
    }

    @Test
    fun `the switch off means silence, and silence leaves no mark`() = runTest {
        aCommittedYesterday()
        settings.setDailyCommit(false)
        assertTrue(runCommit().isEmpty())
        assertNull(state.committedDay())
    }

    @Test
    fun `a revoked permission is a silent no-op, not a crash`() = runTest {
        aCommittedYesterday()
        assertTrue(runCommit(canPost = false).isEmpty())
        assertNull(state.committedDay())
    }

    @Test
    fun `a day the app never saw is not announced — it is not a failed build`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        repository.updateHabit(repository.habit(id)!!.copy(createdAt = yesterday.minusDays(7)))
        // No `day` row for yesterday: `no run`, so no commit and nothing to say.
        assertTrue(runCommit().isEmpty())
    }

    @Test
    fun `a day that graded nothing has a commit but no verdict, so nothing is posted`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        repository.updateHabit(repository.habit(id)!!.copy(createdAt = yesterday.minusDays(7)))
        db.dayDao().markPresent(
            com.callbackdev.thabit.data.db.DayEntity(yesterday.toString(), 0L)
        )
        db.checkDao().upsert(
            com.callbackdev.thabit.data.db.CheckEntity(
                habitId = id,
                date = yesterday.toString(),
                state = "SKIP"
            )
        )
        assertTrue(runCommit().isEmpty())
    }

    // ---- the evening digest ----------------------------------------------

    private suspend fun runDigest(canPost: Boolean = true): List<ThabitNotifications.Content> {
        val posted = mutableListOf<ThabitNotifications.Content>()
        PendingDigestNotifier.evaluate(
            repository = repository,
            settingsStore = settings,
            stateStore = state,
            resources = resources,
            canPost = { canPost },
            post = { posted += it }
        )
        return posted
    }

    @Test
    fun `the digest is opt-in — on by nobody's default`() = runTest {
        repository.addHabit("meditate 10 min")
        assertTrue(runDigest().isEmpty())

        settings.setPendingDigest(true)
        val posted = runDigest()
        assertEquals(1, posted.size)
        assertEquals("1 test still to do", posted.single().title)
        assertEquals(today, state.digestedDay())
    }

    @Test
    fun `nothing open means nothing said, and the day is not marked either`() = runTest {
        settings.setPendingDigest(true)
        val id = repository.addHabit("meditate 10 min")
        repository.pass(id, today)
        assertTrue(runDigest().isEmpty())
        // Not "digested": the hour simply had nothing to report.
        assertNull(state.digestedDay())
    }

    @Test
    fun `a holding avoid test is not something to be reminded about`() = runTest {
        settings.setPendingDigest(true)
        repository.addHabit("no phone after 23:00", type = HabitType.AVOID)
        assertTrue(runDigest().isEmpty())
    }

    @Test
    fun `the digest goes out once an evening`() = runTest {
        settings.setPendingDigest(true)
        repository.addHabit("meditate 10 min")
        assertEquals(1, runDigest().size)
        assertTrue(runDigest().isEmpty())
    }

    // ---- one test's reminder ---------------------------------------------

    private suspend fun runReminder(
        habitId: Long,
        canPost: Boolean = true
    ): List<ThabitNotifications.Content> {
        val posted = mutableListOf<ThabitNotifications.Content>()
        ReminderNotifier.evaluate(
            repository = repository,
            habitId = habitId,
            resources = resources,
            canPost = { canPost },
            post = { _, content -> posted += content }
        )
        return posted
    }

    private suspend fun remindingHabit(
        type: HabitType = HabitType.BOOLEAN,
        schedule: Schedule = Schedule.Daily,
        assert: AssertSpec? = null
    ): Long = repository.addHabit(
        name = "meditate 10 min",
        type = type,
        assert = assert,
        schedule = schedule,
        remindAt = LocalTime.of(7, 0)
    )

    @Test
    fun `a pending test is nudged`() = runTest {
        val id = remindingHabit()
        assertEquals(1, runReminder(id).size)
    }

    @Test
    fun `a test already settled is not`() = runTest {
        val id = remindingHabit()
        repository.pass(id, today)
        assertTrue(runReminder(id).isEmpty())
    }

    @Test
    fun `a skipped test is not nudged either`() = runTest {
        val id = remindingHabit()
        repository.skip(id, today)
        assertTrue(runReminder(id).isEmpty())
    }

    @Test
    fun `an avoid test is nudged while it holds — the intention, arriving on time`() = runTest {
        val id = repository.addHabit(
            name = "no phone after 23:00",
            type = HabitType.AVOID,
            remindAt = LocalTime.of(22, 55)
        )
        assertEquals("holding, it fails only if you break it", runReminder(id).single().summary)
    }

    @Test
    fun `a quota whose week is already met has nothing left to ask for`() = runTest {
        val id = remindingHabit(schedule = Schedule.Quota(1))
        repository.pass(id, today)
        assertTrue(runReminder(id).isEmpty())
    }

    @Test
    fun `a test archived since the alarm was set costs a silent wake-up`() = runTest {
        val id = remindingHabit()
        repository.archiveHabit(id)
        assertTrue(runReminder(id).isEmpty())
    }

    @Test
    fun `a test whose reminder was cleared says nothing`() = runTest {
        val id = remindingHabit()
        repository.updateHabit(repository.habit(id)!!.copy(remindAt = null))
        assertTrue(runReminder(id).isEmpty())
    }

    // ---- the shade's own action ------------------------------------------

    @Test
    fun `pass from the shade writes the check and the day's presence`() = runTest {
        val id = remindingHabit()
        // A fresh install that has never been opened: no `day` row yet.
        db.dayDao().all().let { assertTrue(it.isEmpty()) }

        CheckActionReceiver.pass(repository, id)

        assertEquals("PASS", db.checkDao().find(id, today.toString())?.state)
        // Tapping an action is a deliberate interaction, so the day ran.
        assertEquals(listOf(today.toString()), db.dayDao().all().map { it.date })
    }
}
