package com.callbackdev.thabit

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.callbackdev.thabit.data.ThabitSettings
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.notifications.Reminders
import com.callbackdev.thabit.notifications.ThabitNotifier
import com.callbackdev.thabit.ui.components.EditorOptions
import com.callbackdev.thabit.ui.editor.SuiteFocus
import com.callbackdev.thabit.ui.navigation.ThabitApp
import com.callbackdev.thabit.ui.theme.ThabitTheme
import com.callbackdev.thabit.work.RolloverScheduler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        readFocus(intent)
        watchAlarms()
        // The app is dark-only (see ThabitTheme), so the system bars must always
        // draw their icons light. enableEdgeToEdge()'s default is SystemBarStyle.auto,
        // which picks the appearance from the *system* dark-mode setting: on a phone
        // in light mode that would give dark icons over the Obsidian background — an
        // invisible status bar. Force the dark style on both bars instead (series fix).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            // The theme profile and the editor options are read live from
            // `settings.config`: tapping "dracula" in the file repaints the app
            // on the next frame, with no restart and no separate theme state.
            val settings by remember { ServiceLocator.settings(applicationContext).settings }
                .collectAsStateWithLifecycle(initialValue = ThabitSettings())
            ThabitTheme(profile = settings.theme) {
                ThabitApp(
                    editorOptions = EditorOptions(
                        showLineNumbers = settings.showLineNumbers,
                        wordWrap = settings.wordWrap
                    )
                )
            }
        }
    }

    /**
     * Opening the app is a deliberate interaction, so it stamps the day's
     * presence row (VISION §7) — the evidence that makes `no run` sayable, and
     * the reason a week away comes back as blank days instead of seven failures
     * the app made up.
     *
     * It runs on every start rather than once on create: an app left open across
     * `day_ends` is a new logical day when the user comes back to it, and that
     * day deserves its own row. The write is idempotent, so a start inside a day
     * that already ran costs one ignored insert.
     *
     * The same pass re-aligns the rollover job. That is the safety net VISION §7
     * asks for: a phone asleep at the boundary, a day made 23 hours long by DST
     * or a `day_ends` edit all leave the periodic job pointing at the wrong
     * minute, and the cheapest place to notice is the next time somebody opens
     * the app.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val repository = ServiceLocator.repository(applicationContext)
            repository.markPresent()
            RolloverScheduler.ensureScheduled(applicationContext, repository.boundary())
        }
    }

    /**
     * The alarms, kept in step with what the files say — for as long as the app
     * is in front.
     *
     * A one-shot re-arm on start would have left a real hole: a reminder set at
     * nine in the morning for nine in the evening would not be registered until
     * the *next* time somebody opened the app, and "I set it and it never rang"
     * is the only bug a reminder can have. So the suite and the `notifications`
     * block are **observed**: adding a test, editing its `remind:`, archiving it
     * or moving `digest_hour` re-registers within the same frame that redraws
     * the file.
     *
     * This is also the app-open safety net VISION §7 asks for, applied to the
     * alarms: `repeatOnLifecycle` re-collects on every return to the front and
     * each flow's first emission is the current state, so a fire swallowed by a
     * doze window or lost to a reinstall is put back simply by the app being
     * opened. The registrations are idempotent — same PendingIntents, replaced —
     * so an extra pass costs nothing.
     *
     * Called from `onCreate` and **not** from `onStart`, which is where it first
     * went: `repeatOnLifecycle` already starts and stops itself with the
     * lifecycle, so starting it per start registers a second collector on every
     * return to the front and never lets the first one go. Lint says so out
     * loud (`RepeatOnLifecycleWrongUsage`) — the presence row and the rollover
     * alignment above genuinely belong in `onStart`, this does not.
     */
    private fun watchAlarms() {
        val app = applicationContext
        val clock = ServiceLocator.graph(app).clock
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // The whole suite, archived tests included: `armAll` cancels
                    // what no longer has a next fire.
                    ServiceLocator.repository(app).observeSuite()
                        .distinctUntilChanged()
                        .collect { suite -> Reminders.armAll(app, suite, ZonedDateTime.now(clock)) }
                }
                launch {
                    ServiceLocator.settings(app).settings
                        .map { it.notifications }
                        .distinctUntilChanged()
                        .collect { Reminders.armDigest(app, it, ZonedDateTime.now(clock)) }
                }
            }
        }
    }

    /**
     * The app was already running when the notification was tapped.
     *
     * The content intent carries `FLAG_ACTIVITY_SINGLE_TOP`, so a second tap
     * lands here instead of building a second activity — and the request has to
     * be read out of *this* intent, because [getIntent] still holds the one that
     * launched the app the first time.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFocus(intent)
    }

    /**
     * `- [ ] read 20 pages` — the row a reminder was about.
     *
     * The extra is removed once read: a configuration change re-delivers the
     * launching intent, and a prompt that reopened itself on every rotation
     * would be the app arguing with the reader.
     */
    private fun readFocus(intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, NO_ID)
        if (habitId == NO_ID) return
        intent.removeExtra(EXTRA_HABIT_ID)
        SuiteFocus.request(habitId)
    }

    companion object {
        /** Which test a notification is about — set by [ThabitNotifier]. */
        const val EXTRA_HABIT_ID: String = "com.callbackdev.thabit.extra.HABIT_ID"

        private const val NO_ID = -1L
    }
}
