package com.callbackdev.thabit.work

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.callbackdev.thabit.domain.DayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the day rolls over: the boundary the periodic job aligns itself on, DST
 * nights included.
 */
@RunWith(RobolectricTestRunner::class)
class RolloverSchedulerTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")

    private fun at(date: String, time: String): Instant =
        LocalDateTime.parse("${date}T$time").atZone(rome).toInstant()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext()
        )
    }

    @Test
    fun `the next fire is the end of the current logical day`() {
        val delay = RolloverScheduler.delayUntilNextBoundary(
            at("2026-08-21", "22:00"), rome, DayBoundary.Default
        )
        assertEquals(Duration.ofHours(2), delay)
    }

    @Test
    fun `a late day_ends is honoured`() {
        val boundary = DayBoundary(LocalTime.of(3, 0))
        // 01:00 on the 21st is still the 20th's day: it ends in two hours.
        assertEquals(
            Duration.ofHours(2),
            RolloverScheduler.delayUntilNextBoundary(at("2026-08-21", "01:00"), rome, boundary)
        )
        // 04:00 belongs to the 21st: its end is 23 hours away.
        assertEquals(
            Duration.ofHours(23),
            RolloverScheduler.delayUntilNextBoundary(at("2026-08-21", "04:00"), rome, boundary)
        )
    }

    @Test
    fun `standing exactly on the boundary schedules the next day, never zero`() {
        val delay = RolloverScheduler.delayUntilNextBoundary(
            at("2026-08-21", "00:00"), rome, DayBoundary.Default
        )
        assertTrue(delay > Duration.ZERO)
        assertEquals(Duration.ofHours(24), delay)
    }

    @Test
    fun `the spring-forward night is one hour shorter and the job follows`() {
        // 29 March 2026 in Rome loses an hour: the day ends 23 hours after it began.
        val delay = RolloverScheduler.delayUntilNextBoundary(
            at("2026-03-29", "00:00"), rome, DayBoundary.Default
        )
        assertEquals(Duration.ofHours(23), delay)
    }

    @Test
    fun `the fall-back night is one hour longer and the job follows`() {
        val delay = RolloverScheduler.delayUntilNextBoundary(
            at("2026-10-25", "00:00"), rome, DayBoundary.Default
        )
        assertEquals(Duration.ofHours(25), delay)
    }

    @Test
    fun `scheduling twice keeps exactly one job`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        RolloverScheduler.ensureScheduled(
            context, DayBoundary.Default, at("2026-08-21", "22:00"), rome
        )
        RolloverScheduler.ensureScheduled(
            context, DayBoundary(LocalTime.of(3, 0)), at("2026-08-21", "22:00"), rome
        )

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RolloverScheduler.WORK_NAME)
            .get()
        assertEquals(1, infos.count { it.state != WorkInfo.State.CANCELLED })
    }

    @Test
    fun `cancelling leaves nothing enqueued`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        RolloverScheduler.ensureScheduled(
            context, DayBoundary.Default, at("2026-08-21", "22:00"), rome
        )
        RolloverScheduler.cancel(context)
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RolloverScheduler.WORK_NAME)
            .get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
