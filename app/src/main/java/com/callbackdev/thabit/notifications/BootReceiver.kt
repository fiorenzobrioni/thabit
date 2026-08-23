package com.callbackdev.thabit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.work.RolloverScheduler
import kotlinx.coroutines.launch

/**
 * Everything that invalidates an alarm without anybody opening the app.
 *
 * Alarms do not survive a reboot, a reinstall, or a user moving the clock — and
 * the rollover job, which WorkManager *does* keep, is aligned on a boundary that
 * a timezone change moves under it. All four events mean the same thing: what is
 * registered no longer matches what the suite asks for, so it is registered
 * again from the stored suite.
 *
 * Re-arming is idempotent by construction (the same PendingIntents, replaced),
 * so being woken by two of these in a row costs nothing.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val app = context.applicationContext
        val pending = goAsync()
        ServiceLocator.graph(app).appScope.launch {
            try {
                Reminders.rearmAll(app, ServiceLocator.graph(app).clock)
                RolloverScheduler.ensureScheduled(app, ServiceLocator.repository(app).boundary())
            } finally {
                pending.finish()
            }
        }
    }
}
