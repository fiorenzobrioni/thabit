package com.callbackdev.thabit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callbackdev.thabit.domain.DayBoundary
import com.callbackdev.thabit.ui.theme.ThemeProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * `settings.config` as data — the minimal store Fase 2 needs and Fase 4 dresses.
 *
 * Only the settings the domain actually reads live here for now: the logical day
 * boundary, the week the display grids start on, the theme profile and the two
 * editor toggles. Notifications and export arrive with their own phases rather
 * than as empty keys nothing writes.
 *
 * `week_starts` deliberately does **not** reach [com.callbackdev.thabit.domain.IsoWeek]:
 * it moves the heatmap columns and the README's seven-day table, while quota
 * verdicts, week separators and `perfect-week` stay ISO (Monday-based). A record
 * whose meaning changed when a display preference flipped would not be
 * recomputable from an export.
 */
data class ThabitSettings(
    val dayEnds: LocalTime = LocalTime.MIDNIGHT,
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val theme: ThemeProfile = ThemeProfile.Obsidian,
    val showLineNumbers: Boolean = false,
    val wordWrap: Boolean = false,
    /**
     * Epoch millis of the first change the user ever made, or null while the
     * file is still exactly what shipped.
     *
     * It drives the `// Last modified:` line, which is absent rather than
     * showing an install date nobody chose: an untouched config has not been
     * modified, and saying otherwise would be the file inventing an edit.
     */
    val lastModified: Long? = null
) {
    val boundary: DayBoundary get() = DayBoundary(dayEnds)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

class SettingsStore(
    private val store: DataStore<Preferences>,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<ThabitSettings> = store.data
        // A corrupted preferences file must not take the app down with it: the
        // defaults are honest values, not a crash on launch.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            ThabitSettings(
                dayEnds = prefs[Keys.DayEnds]
                    ?.let { runCatching { LocalTime.parse(it, HHMM) }.getOrNull() }
                    ?: LocalTime.MIDNIGHT,
                weekStartsOn = prefs[Keys.WeekStart]
                    ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                    ?: DayOfWeek.MONDAY,
                theme = prefs[Keys.Theme]?.let { ThemeProfile.fromName(it) }
                    ?: ThemeProfile.Obsidian,
                showLineNumbers = prefs[Keys.LineNumbers] ?: false,
                wordWrap = prefs[Keys.WordWrap] ?: false,
                lastModified = prefs[Keys.LastModified]
            )
        }

    suspend fun setDayEnds(time: LocalTime) = edit { it[Keys.DayEnds] = time.format(HHMM) }

    suspend fun setWeekStart(day: DayOfWeek) = edit { it[Keys.WeekStart] = day.name }

    suspend fun setTheme(profile: ThemeProfile) = edit { it[Keys.Theme] = profile.name }

    suspend fun setShowLineNumbers(enabled: Boolean) = edit { it[Keys.LineNumbers] = enabled }

    suspend fun setWordWrap(enabled: Boolean) = edit { it[Keys.WordWrap] = enabled }

    /**
     * `$ git restore settings.config`.
     *
     * It clears the config and **nothing else**: the suite and every check row
     * live in Room and are never touched here. Resetting a preference must never
     * cost the user a day of their history (VISION §4.4).
     *
     * It also does not go through [edit], so the `// Last modified:` line goes
     * away with everything else: a restored file is not a modified file, and
     * leaving the stamp behind would have it claim an edit that was undone.
     */
    suspend fun restoreDefaults() {
        store.edit { it.clear() }
    }

    /**
     * Every write stamps the file as modified.
     *
     * It lives in the one place every setter goes through rather than in each of
     * them: a setting added later cannot forget to declare that the file changed,
     * which is the sort of omission that turns a stated fact into a stale one.
     */
    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { prefs ->
            block(prefs)
            prefs[Keys.LastModified] = clock.millis()
        }
    }

    private object Keys {
        val DayEnds = stringPreferencesKey("day_ends")
        val WeekStart = stringPreferencesKey("week_starts")
        val Theme = stringPreferencesKey("theme")
        val LineNumbers = booleanPreferencesKey("line_numbers")
        val WordWrap = booleanPreferencesKey("word_wrap")
        val LastModified = longPreferencesKey("last_modified")
    }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
