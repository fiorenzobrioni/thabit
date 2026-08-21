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
            CodeCanvas(lines = lines, state = listState, modifier = Modifier.fillMaxSize())
        }
        TerminalStatusBar {
            StatusBarStart { StatusBarText("⎇ main") }
            // `ro` is the truth here: the README is written by the app, from the
            // rows the other file holds. Nothing on this screen answers a tap.
            StatusBarText("ro")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 640)
@Composable
private fun ReadmeScreenPreview() {
    ThabitTheme {
        ReadmeScreen(state = EditorUiState(loading = false))
    }
}
