package com.callbackdev.thabit.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.EditorNavBar
import com.callbackdev.thabit.ui.components.EditorNavItems
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.editor.HabitsTestScreen
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * The shell: the editor bottom bar over one file per tab.
 *
 * Still without Navigation Compose — the per-tab NavHost with saved stacks is
 * Fase 4 work. What changed in Fase 3 is that the editor tab is no longer a
 * sample: it is the live `habits.test`, reading the database and writing the
 * user's checks. The other three tabs keep saying honestly that their file is
 * not written yet.
 */
@Composable
fun ThabitApp() {
    var selectedRoute by rememberSaveable { mutableStateOf(EditorNavItems.Editor.route) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                if (selectedRoute == EditorNavItems.Editor.route) {
                    HabitsTestScreen()
                } else {
                    val syntax = ThabitTheme.syntax
                    val lines = remember(selectedRoute, syntax) {
                        when (selectedRoute) {
                            EditorNavItems.Log.route -> placeholder("habits_history.diff", syntax)
                            EditorNavItems.Stats.route -> placeholder("stats.md", syntax)
                            else -> placeholder("settings.config", syntax)
                        }
                    }
                    CodeCanvas(lines = lines)
                }
            }
            EditorNavBar(
                items = EditorNavItems.All,
                isSelected = { it.route == selectedRoute },
                onSelect = { selectedRoute = it.route }
            )
        }
    }
}

/** The honest empty tab: the file exists in the plan, not yet in the app. */
private fun placeholder(fileName: String, syntax: SyntaxColors) = listOf(
    commentLine(
        // Placeholders are terminal output, so the comment marker follows the
        // future host file's syntax (VISION §1.1): # for yaml/md/diff-header
        // territory, // for the JSON-style settings.config.
        if (fileName == "settings.config") "// $fileName — not yet written"
        else "# $fileName — not yet written",
        syntax
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 480)
@Composable
private fun ThabitAppPreview() {
    ThabitTheme {
        ThabitApp()
    }
}
