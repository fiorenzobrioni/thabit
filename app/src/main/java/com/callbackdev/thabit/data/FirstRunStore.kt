package com.callbackdev.thabit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.firstRunDataStore by preferencesDataStore(name = "first_run")

/** What the shell has to know before it can draw anything — see [FirstRunStore.state]. */
enum class FirstRun {
    /** The legacy check has not landed yet in this process: draw nothing, not `init`. */
    Unknown,

    /** `$ thabit init` still owes an answer. */
    Pending,

    /** Answered — by writing a test or by skipping — or inherited by an upgrade. */
    Done
}

/**
 * Whether the first run still owes an answer (Fase 14, the siblings' pattern
 * ported: tweather's Fase 14c, tsteps' Fase 17).
 *
 * Its own DataStore rather than a corner of [SettingsStore] or [WorkspaceStore]:
 * this is neither a line of `settings.config` — there is no setting called "have
 * you been introduced" — nor editor session state. It is a fact about the
 * install, two booleans, each written once and then never again.
 */
class FirstRunStore(private val dataStore: DataStore<Preferences>) {

    val state: Flow<FirstRun> = dataStore.data
        // A corrupt preferences file must not lock the app out of its own
        // workspace: empty reads as [FirstRun.Unknown], which draws nothing and
        // waits for the check to land, exactly as a first frame does.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            when {
                prefs[Migrated] != true -> FirstRun.Unknown
                prefs[InitDone] == true -> FirstRun.Done
                else -> FirstRun.Pending
            }
        }
        .distinctUntilChanged()

    /**
     * Decides once per install whether it predates `$ thabit init`, and never
     * runs again.
     *
     * [used] is what tells an upgrade from a fresh install: an app that already
     * holds a test — archived ones count, they were written by somebody — or a
     * single check row has been answering this question by itself for as long
     * as it has been installed, and must not be asked it again by an update.
     *
     * Presence rows are deliberately **not** part of [used], even though they
     * are the app's other write: `markPresent()` fires from `onStart`, one
     * lifecycle step after the call site of this method, so a fresh install
     * would race itself into "already used" on its very first launch. What the
     * user wrote is the honest signal; that the app was opened is not.
     */
    suspend fun migrate(used: Boolean) {
        dataStore.edit { prefs ->
            if (prefs[Migrated] == true) return@edit
            prefs[Migrated] = true
            if (used) prefs[InitDone] = true
        }
    }

    /** The init screen has been answered — skipping is an answer too. */
    suspend fun markInitDone() {
        dataStore.edit { it[InitDone] = true }
    }

    companion object {
        private val Migrated = booleanPreferencesKey("first_run_migrated")
        private val InitDone = booleanPreferencesKey("init_done")

        fun create(context: Context) = FirstRunStore(context.firstRunDataStore)
    }
}
