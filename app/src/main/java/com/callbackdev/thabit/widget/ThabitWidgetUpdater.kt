package com.callbackdev.thabit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.Verdicts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Re-renders every widget instance from persisted state.
 *
 * Called at the rollover, after a check written from a notification or from the
 * widget itself, from the app while it is in front, and on the provider's own
 * `onUpdate`. A no-op with zero widgets, so every one of those callers can fire
 * it without asking first.
 *
 * It **reads only**. The day it renders is the logical day the repository is on
 * right now, and it is put on screen rather than assumed (VISION §4.6): a widget
 * cannot notice it has gone stale, so the honest defence is to say which day it
 * is showing.
 */
object ThabitWidgetUpdater {

    suspend fun updateAll(context: Context) {
        val ids = ThabitWidgetProvider.ids(context)
        if (ids.isEmpty()) return

        val repository = ServiceLocator.repository(context)
        val settings = ServiceLocator.settings(context).settings.first()
        val today = repository.today()
        val history = repository.fullHistory()

        val data = WidgetData(
            date = today,
            outcomes = Verdicts.outcomesOn(history, today, today),
            suiteSize = history.habits.count { it.isActiveOn(today) }
        )
        val palette = widgetPalette(settings.theme.name)
        val resources = context.resources

        // Off the caller's thread on purpose. The sizes map is one RemoteViews
        // per rung, and every row of every rung registers its own PendingIntent
        // with the system — dozens of IPCs. The app's own collector calls this
        // on every check written, which is the main thread, and that is the one
        // place a burst of IPC turns into a dropped frame under the very tap
        // that caused it.
        val views = withContext(Dispatchers.Default) {
            WidgetRenderer.sizeMap(
                context,
                content = { tier -> WidgetContentBuilder.build(data, tier, resources) },
                palette = palette,
                opacityPct = settings.widgetOpacityPct
            )
        }
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }
}
