package com.callbackdev.thabit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.notificationStateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notif_state"
)

/**
 * What has already been said, so it is never said twice.
 *
 * Both notifications the app posts by itself are **once a day** events: the
 * commit of the day that just closed, and the evening digest. A periodic worker
 * that runs twice, an alarm the OS batches late and a reboot in between are all
 * ordinary, so "did I already post this day's?" has to be a stored fact rather
 * than a hope about scheduling.
 *
 * It is its own store and deliberately **not** `settings.config` (tsteps' rule,
 * and tweather's `alerts` before it): this is machine state the user never
 * edits, and `$ git restore settings.config` must not re-fire yesterday's
 * notification as a side effect of putting the line numbers back.
 *
 * Nothing here is a verdict. The dedup dates say what was *announced*, never
 * what happened — the day's result is still derived from the check rows every
 * time anybody asks (VISION §6.8).
 */
class NotificationStateStore(private val store: DataStore<Preferences>) {

    /** The last logical day whose commit was announced, or null. */
    suspend fun committedDay(): LocalDate? = read(Keys.CommittedDay)

    suspend fun markCommitted(date: LocalDate) = write(Keys.CommittedDay, date)

    /** The last logical day the evening digest went out on, or null. */
    suspend fun digestedDay(): LocalDate? = read(Keys.DigestedDay)

    suspend fun markDigested(date: LocalDate) = write(Keys.DigestedDay, date)

    private suspend fun read(key: Preferences.Key<String>): LocalDate? =
        store.data.first()[key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private suspend fun write(key: Preferences.Key<String>, date: LocalDate) {
        store.edit { it[key] = date.toString() }
    }

    private object Keys {
        val CommittedDay = stringPreferencesKey("committed_day")
        val DigestedDay = stringPreferencesKey("digested_day")
    }

    companion object {
        fun create(context: Context) =
            NotificationStateStore(context.applicationContext.notificationStateDataStore)
    }
}
