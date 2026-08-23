package com.callbackdev.thabit.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.NotificationSettings
import com.callbackdev.thabit.domain.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The registrations themselves: one alarm per test, and each of them its own.
 *
 * The assertion that matters is the second one. `PendingIntent` and
 * `AlarmManager` both compare intents with `filterEquals`, which ignores
 * extras — so two reminders that differed only by an id extra would be **the
 * same alarm**: arming the second would replace the first, and cancelling either
 * would cancel both. Distinct request codes and distinct data Uris are what keep
 * fifteen reminders fifteen reminders.
 */
@RunWith(RobolectricTestRunner::class)
class RemindersTest {

    private lateinit var context: Context
    private lateinit var alarms: AlarmManager

    private val now = ZonedDateTime.of(
        LocalDate.of(2026, 8, 20),
        LocalTime.of(6, 0),
        ZoneId.of("Europe/Rome")
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private fun habit(id: Long, remindAt: LocalTime?) = Habit(
        id = id,
        name = "test $id",
        remindAt = remindAt,
        createdAt = LocalDate.of(2026, 8, 1)
    )

    private fun scheduled() = shadowOf(alarms).scheduledAlarms

    @Test
    fun `a test with a reminder gets an alarm at its next occurrence`() {
        Reminders.arm(context, habit(1L, LocalTime.of(7, 0)), now)
        val alarm = scheduled().single()
        assertEquals(
            now.withHour(7).withMinute(0).toInstant().toEpochMilli(),
            alarm.triggerAtTime
        )
        // Inexact by design: a window, and therefore no exact-alarm permission
        // to ask anybody for (VISION §6.7).
        assertEquals(Reminders.WINDOW_MILLIS, alarm.windowLengthMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
    }

    @Test
    fun `two tests are two alarms, not one overwriting the other`() {
        Reminders.arm(context, habit(1L, LocalTime.of(7, 0)), now)
        Reminders.arm(context, habit(2L, LocalTime.of(21, 0)), now)
        assertEquals(2, scheduled().size)

        // And cancelling one leaves the other exactly where it was.
        Reminders.cancel(context, 1L)
        val left = scheduled().single()
        assertEquals(now.withHour(21).withMinute(0).toInstant().toEpochMilli(), left.triggerAtTime)
    }

    @Test
    fun `arming a test that has no reminder is how its old alarm goes away`() {
        Reminders.arm(context, habit(1L, LocalTime.of(7, 0)), now)
        assertTrue(scheduled().isNotEmpty())
        Reminders.arm(context, habit(1L, null), now)
        assertTrue(scheduled().isEmpty())
    }

    @Test
    fun `the digest is registered only while it is switched on`() {
        Reminders.armDigest(context, NotificationSettings(pendingDigest = false), now)
        assertTrue(scheduled().isEmpty())

        Reminders.armDigest(
            context,
            NotificationSettings(pendingDigest = true, digestHour = LocalTime.of(20, 0)),
            now
        )
        assertEquals(
            now.withHour(20).withMinute(0).toInstant().toEpochMilli(),
            scheduled().single().triggerAtTime
        )

        // Switched back off, the registration goes with it.
        Reminders.armDigest(context, NotificationSettings(pendingDigest = false), now)
        assertTrue(scheduled().isEmpty())
    }

    @Test
    fun `the digest never collides with a test's own alarm`() {
        Reminders.arm(context, habit(1L, LocalTime.of(20, 0)), now)
        Reminders.armDigest(
            context,
            NotificationSettings(pendingDigest = true, digestHour = LocalTime.of(20, 0)),
            now
        )
        // Same minute, same type, same receiver: two registrations all the same.
        assertEquals(2, scheduled().size)
    }
}
