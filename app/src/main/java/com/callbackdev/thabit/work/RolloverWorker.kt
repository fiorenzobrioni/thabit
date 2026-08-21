package com.callbackdev.thabit.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * What happens at `day_ends` — and, far more importantly, what does not.
 *
 * **This worker never writes.** Not a verdict, not a summary, not a presence
 * row. The commit is a boundary, not a write (VISION §7): at `day_ends` the day
 * becomes read-only by definition, and every verdict about it is derived from
 * the checks that were already there. There is nothing to freeze, so there is
 * nothing to get wrong, and `--amend` recomputes the lot for free (§6.8).
 *
 * Presence in particular is off limits here. A worker stamping a `day` row would
 * invent a user who was not there — every day with the phone switched on would
 * become a day that "ran", `no run` would stop meaning anything, and coverage
 * would report a number nobody earned.
 *
 * So the job has exactly two effects, both of them repaints:
 *
 * - post the `daily_commit` notification for the day that just closed (Fase 9)
 * - repaint the widget so it stops showing yesterday's suite (Fase 10)
 *
 * Until those phases land, [RolloverEffects.None] does nothing at all and the
 * job's only real work is keeping itself aligned with the boundary — which is
 * worth having on device now, because DST and `day_ends` edits are exactly the
 * things one wants to have been running for a while before trusting them.
 */
class RolloverWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val effects = RolloverEffects.current
        effects.onDayClosed(applicationContext)
        return Result.success()
    }
}

/**
 * The rollover's side effects, injected so the worker's "never writes" contract
 * is a property of a small interface rather than a promise in a comment.
 *
 * Fase 9 supplies the notification, Fase 10 the widget repaint. Neither is
 * allowed to write data, and both are given only a [Context].
 */
fun interface RolloverEffects {

    suspend fun onDayClosed(context: Context)

    companion object {
        /** Nothing to do yet: the notification and the widget are later phases. */
        val None = RolloverEffects { }

        @Volatile
        var current: RolloverEffects = None
            private set

        fun install(effects: RolloverEffects) {
            current = effects
        }
    }
}
