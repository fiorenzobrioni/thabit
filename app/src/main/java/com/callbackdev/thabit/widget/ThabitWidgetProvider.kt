package com.callbackdev.thabit.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.callbackdev.thabit.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * The `thabit --status` home widget.
 *
 * Passive on battery, like both siblings: no `updatePeriodMillis`, no polling of
 * its own. It renders persisted state and is repainted by whoever moves that
 * state — the rollover at `day_ends`, a check written from the shade or the
 * widget itself, and the app while it is in front.
 *
 * **A repaint is not presence.** Nothing here writes the `day` row (VISION §7):
 * a widget somebody glanced at and never touched testifies to nothing, and a
 * rollover that stamped presence on its own would turn every day with the phone
 * switched on into a day that "ran" — which would quietly delete the whole
 * meaning of `no run`. Only the tap on a row writes, and it writes through
 * [com.callbackdev.thabit.notifications.CheckActionReceiver], which stamps
 * presence because a tap is a person.
 */
class ThabitWidgetProvider : AppWidgetProvider() {

    // The hook only records what the broadcast needs; the suspend work runs once
    // in onReceive. goAsync() is consume-once, and a single broadcast can hit two
    // hooks (ACTION_APPWIDGET_ENABLE_AND_UPDATE → onEnabled + onUpdate), where a
    // second goAsync() would return null — the siblings' crash, kept fixed.
    private var needsRender = false

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        needsRender = true
    }

    /**
     * Resize is deliberately NOT handled: the sizes map exists so the host
     * re-picks the right tier itself, in-process. Pushing a fresh RemoteViews
     * from here raced that and left shrunk widgets showing a clipped transcript.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) = Unit

    override fun onEnabled(context: Context) {
        needsRender = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        needsRender = false
        super.onReceive(context, intent)
        if (!needsRender) return

        // Nullable despite the platform signature: goAsync() only returns a
        // result while a real broadcast is being dispatched.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        ServiceLocator.graph(context.applicationContext).appScope.launch {
            try {
                ThabitWidgetUpdater.updateAll(context.applicationContext)
            } catch (e: Exception) {
                // An unhandled throw here would crash the app from a broadcast;
                // the widget simply keeps whatever it was showing.
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, ThabitWidgetProvider::class.java))
    }
}
