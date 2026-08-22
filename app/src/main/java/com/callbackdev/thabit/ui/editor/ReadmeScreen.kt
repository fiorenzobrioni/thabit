package com.callbackdev.thabit.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.buildMarkdownLines
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * `README.md` — the day as prose, rendered as **source**.
 *
 * The GitHub "Code" view and not the Preview, like the siblings: a rendered
 * document with proportional headings would break JetBrains Mono, the 4px grid
 * and the gutter, and this app has no preview mode to switch to. The markdown is
 * built by [ReadmeDocument] from resources — this is the only file in thabit
 * whose words are localized, headings included.
 */
@Composable
fun ReadmeScreen(
    state: EditorUiState,
    onAddTest: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val syntax = ThabitTheme.syntax
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]

    val lines = remember(state, syntax, locale) {
        if (state.loading) {
            emptyList()
        } else {
            buildMarkdownLines(
                markdown = ReadmeDocument.build(
                    history = state.history,
                    today = state.today,
                    weekStartsOn = state.weekStartsOn,
                    locale = locale,
                    resources = resources
                ),
                syntax = syntax
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            CodeCanvas(
                lines = lines,
                state = listState,
                // The same clearance the suite gets: the footer that says how
                // many days this was computed on is the last line of the file,
                // and a fact hidden under the glow is a fact the reader has to
                // scroll past the end to find.
                contentPadding = EditorFileClearance,
                modifier = Modifier.fillMaxSize()
            )
            // The tab's verb, not the suite's: reading what the day looks like
            // is when a missing test is easiest to notice.
            AddTestFab(onClick = onAddTest)
        }
        TerminalStatusBar {
            StatusBarStart { StatusBarText("⎇ main") }
            // `ro` is the truth here: the README is written by the app, from the
            // rows the other file holds. The FAB does not make it `rw` — it
            // writes a test into `habits.test`, and this file only reports what
            // that file says.
            StatusBarText("ro")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 640)
@Composable
private fun ReadmeScreenPreview() {
    ThabitTheme {
        ReadmeScreen(state = EditorUiState(loading = false), onAddTest = {})
    }
}
