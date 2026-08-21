package com.callbackdev.thabit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace")

/** The two files open in the editor tab's bar. */
enum class EditorFile { TEST, README }

/**
 * Editor workspace state — what an editor keeps in its *session*, not in its
 * settings.
 *
 * The last file open in the editor tab survives a restart, the way VS Code
 * reopens yesterday's tab. Its own DataStore rather than a [SettingsStore] key,
 * and that is the whole point of the separation: `$ git restore settings.config`
 * must not close the tab somebody was reading, and there is no line in
 * `settings.config` that says which file is open — because it is not a setting.
 *
 * Ported from tsteps, where the same store holds the same idea.
 */
class WorkspaceStore(private val dataStore: DataStore<Preferences>) {

    val editorFile: Flow<EditorFile> = dataStore.data
        // A corrupt preferences file must not take the editor down with it: the
        // worst honest outcome is opening on the suite, which is the default.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            prefs[ActiveFile]?.let { name -> EditorFile.entries.firstOrNull { it.name == name } }
                ?: EditorFile.TEST
        }
        .distinctUntilChanged()

    suspend fun setEditorFile(file: EditorFile) {
        dataStore.edit { it[ActiveFile] = file.name }
    }

    companion object {
        private val ActiveFile = stringPreferencesKey("editor_active_file")

        fun create(context: Context) = WorkspaceStore(context.workspaceDataStore)
    }
}
