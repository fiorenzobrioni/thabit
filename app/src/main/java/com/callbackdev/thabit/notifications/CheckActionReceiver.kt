package com.callbackdev.thabit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callbackdev.thabit.MainActivity
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.WriteOutcome
import com.callbackdev.thabit.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * `[pass]` from the shade — the one place outside the app that writes.
 *
 * It is a broadcast and not a trampoline into the activity, which is both what
 * Android 12 requires and what the feature is for: a test that can only be
 * ticked by opening the app has not been ticked from the notification at all.
 *
 * Two writes, in this order and no other:
 *
 * 1. **presence** — tapping a notification action is a deliberate interaction,
 *    so the day gets its `day` row and stops being a `no run` (VISION §7). It is
 *    written first because it is true whatever the second write answers: the
 *    user was here even if the day turns out to be read-only.
 * 2. **the check**, through the repository, which is where the amend window is
 *    enforced. A notification that fired at 23:58 and was tapped at 00:03 writes
 *    on the day it can still write to, or on none at all — the receiver does not
 *    get to bypass a rule the screens obey.
 */
class CheckActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PASS) return
        val app = context.applicationContext
        val habitId = intent.getLongExtra(MainActivity.EXTRA_HABIT_ID, NO_ID)
        if (habitId == NO_ID) return
        val pending = goAsync()
        ServiceLocator.graph(app).appScope.launch {
            try {
                pass(ServiceLocator.repository(app), habitId)
                // The nudge has been answered: it goes away by itself, the way a
                // notification with an action people actually use has to.
                ThabitNotifier.cancelReminder(app, habitId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PASS = "com.callbackdev.thabit.action.PASS"
        private const val NO_ID = -1L

        /**
         * The two writes, without an Android broadcast around them.
         *
         * Split out so the rule this action has to obey — presence first, then a
         * check the repository is still free to refuse — is asserted directly
         * instead of through a shadowed system service.
         */
        suspend fun pass(repository: HabitRepository, habitId: Long): WriteOutcome {
            val today = repository.markPresent()
            return repository.pass(habitId, today)
        }
    }
}
