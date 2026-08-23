package com.callbackdev.thabit.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callbackdev.thabit.notifications.DailyCommitNotifier

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
 * Fase 9 supplied the first of the two. The widget's repaint joins it in Fase 10
 * without changing anything here: the effect is still handed nothing but a
 * [Context], which is what keeps "never writes" a property of a small interface
 * rather than a promise in a comment.
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
 *
 * The real effect is the **default** rather than something an `Application`
 * installs at startup, and that is deliberate: WorkManager can start this
 * process on its own at `day_ends` with no screen involved, and a static that
 * somebody has to remember to fill would leave that run posting nothing. A field
 * whose correct value depends on a startup hook having run is a field that will
 * eventually be empty.
 */
fun interface RolloverEffects {

    suspend fun onDayClosed(context: Context)

    companion object {
        /** What a test installs when the day's close must have no side effect. */
        val None = RolloverEffects { }

        /** The day that just closed, announced once (Fase 9). */
        val Default = RolloverEffects { context -> DailyCommitNotifier.run(context) }

        @Volatile
        var current: RolloverEffects = Default
            private set

        fun install(effects: RolloverEffects) {
            current = effects
        }

        /** Back to the shipping behaviour — what a test's teardown restores. */
        fun reset() {
            current = Default
        }
    }
}
