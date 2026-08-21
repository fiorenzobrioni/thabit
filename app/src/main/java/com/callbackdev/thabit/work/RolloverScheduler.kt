package com.callbackdev.thabit.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.callbackdev.thabit.domain.DayBoundary
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * When the day rolls over, and how the app is woken for it.
 *
 * One shared periodic job (series rule), not a chain that re-arms itself: a
 * chain loses everything on a single missed fire, while WorkManager keeps a
 * periodic request across reboots and process death. A day that is 23 or 25
 * hours long slips the fire by an hour — which is why [ensureScheduled] runs on
 * every app open and after every `day_ends` edit, and why the safety net at
 * app open exists at all.
 */
object RolloverScheduler {

    const val WORK_NAME = "thabit-rollover"

    /**
     * Time from [now] to the next `day_ends` boundary.
     *
     * Never zero: standing exactly on the boundary means this day has just
     * ended, so the next fire is a whole day away.
     */
    fun delayUntilNextBoundary(now: Instant, zone: ZoneId, boundary: DayBoundary): Duration {
        val today = boundary.logicalDate(now, zone)
        val end = boundary.endOf(today, zone)
        val delay = Duration.between(now, end)
        return if (delay.isZero || delay.isNegative) {
            Duration.between(now, boundary.endOf(today.plusDays(1), zone))
        } else {
            delay
        }
    }

    /**
     * (Re)registers the periodic job aligned on the next boundary.
     *
     * [ExistingPeriodicWorkPolicy.UPDATE] so a `day_ends` edit re-aligns the
     * existing job instead of stacking a second one, and so an app open never
     * resets a job that is already correctly scheduled.
     */
    fun ensureScheduled(
        context: Context,
        boundary: DayBoundary,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val request = PeriodicWorkRequestBuilder<RolloverWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextBoundary(now, zone, boundary).toMinutes(), TimeUnit.MINUTES)
            // No constraints: the job neither needs the network (there is none)
            // nor charge. It posts a notification and repaints a widget.
            .setConstraints(Constraints.NONE)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
