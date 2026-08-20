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
import com.callbackdev.thabit.ui.components.CheckboxState
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.EditorNavBar
import com.callbackdev.thabit.ui.components.EditorNavItems
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.components.yamlTestLine
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * Provisional Fase 1 shell: the editor bottom bar over one placeholder per tab,
 * WITHOUT Navigation Compose — the real NavHost with per-tab stacks is Fase 4
 * work (series pattern). It exists so the ported kit and the new YAML tokenizer
 * are exercised on device from day one: the editor tab renders a static
 * `habits.test` through CodeCanvas + YamlSyntax (it replaced Fase 0's
 * hand-drawn SkeletonScreen), the other tabs state honestly that their file is
 * not yet written.
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
                val syntax = ThabitTheme.syntax
                val lines = remember(selectedRoute, syntax) {
                    when (selectedRoute) {
                        EditorNavItems.Editor.route -> sampleSuite(syntax)
                        EditorNavItems.Log.route -> placeholder("habits_history.diff", syntax)
                        EditorNavItems.Stats.route -> placeholder("stats.md", syntax)
                        else -> placeholder("settings.config", syntax)
                    }
                }
                CodeCanvas(lines = lines)
            }
            EditorNavBar(
                items = EditorNavItems.All,
                isSelected = { it.route == selectedRoute },
                onSelect = { selectedRoute = it.route }
            )
        }
    }
}

/** Static sample suite: the Fase 3 screen's shape, drawn with the real tokenizer. */
private fun sampleSuite(syntax: SyntaxColors) = listOf(
    commentLine("# habits.test", syntax),
    commentLine("# suite — 3 passed · 2 pending · 1 skipped", syntax),
    commentLine("#", syntax),
    yamlTestLine(CheckboxState.Passed, "meditate 10 min", syntax, comment = "07:12"),
    yamlTestLine(CheckboxState.Passed, "read 20 pages 📖", syntax, comment = "23 pages"),
    yamlTestLine(CheckboxState.Pending, "pushups", syntax, comment = "12/30    [+1]"),
    yamlTestLine(CheckboxState.Skipped, "run 5k", syntax, comment = "skip: rest day"),
    yamlTestLine(CheckboxState.Holding, "no sugar", syntax, comment = "holds — asserts at commit"),
    yamlTestLine(CheckboxState.Passed, "journal", syntax, comment = "21:40"),
    commentLine("#", syntax),
    commentLine("# static sample — the live suite arrives with Fase 3", syntax)
)

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
