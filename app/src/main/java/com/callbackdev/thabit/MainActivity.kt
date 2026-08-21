package com.callbackdev.thabit

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.ui.navigation.ThabitApp
import com.callbackdev.thabit.ui.theme.ThabitTheme
import com.callbackdev.thabit.work.RolloverScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
            ThabitTheme {
                // Fase 1 shell: editor bottom bar + one placeholder per tab.
                // Theme switching at runtime arrives with settings (Fase 4).
                ThabitApp()
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
}
