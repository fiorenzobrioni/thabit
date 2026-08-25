package com.callbackdev.thabit.ui.settings

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
import com.callbackdev.thabit.R
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.EditorTabs
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.buildMarkdownLines
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * `HELP.md` — the second file behind the Settings tab bar (Fase 14), and the
 * app's answer to "what is a commit?" for somebody who does not read `git` for
 * a living.
 *
 * Deliberately a file and not an intro carousel: a definition offered before you
 * have seen the thing it defines does not stick, and a screen shown once cannot
 * be consulted the day the question actually arrives. Developers learn a tool
 * from its `--help`, not from slides — so the explanation lives where it can be
 * re-opened forever, and the first run only points at it (the one-shot hint at
 * the head of `habits.test`).
 *
 * It lands on the Settings tab rather than in the editor because the editor's
 * two files are about *the day*, and this one is about *the app*. Settings is
 * also where somebody goes when a product has confused them.
 *
 * The division of labour with the `README.md` tab is deliberate and is the
 * reason neither file repeats the other: `README.md` glosses the words that
 * stand next to a number the reader is looking at right now (`build`, `health`,
 * `coverage`, `flaky`, `regression` — Fase 13's audit put them there), and this
 * file explains the *shape* of the app: what the tabs are, what a test and a
 * commit are, where the numbers come from. Two truths to keep aligned is one
 * too many, so the last line of the vocabulary section points at the other file
 * rather than restating it.
 *
 * Prose, so fully localized, headings included — the same rule the `README.md`
 * tab follows. The words in `code spans` are the app's own file and key names.
 */
@Composable
fun HelpScreen(
    onSelectFile: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val syntax = ThabitTheme.syntax
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]

    // One resource item per rendered line: the document is markdown SOURCE, and
    // a real newline inside an Android string resource is collapsed into a space.
    val lines = remember(syntax, locale) {
        buildMarkdownLines(resources.getStringArray(R.array.help_md).toList(), syntax)
    }

    Column(modifier.fillMaxSize()) {
        EditorTabs(
            fileNames = SETTINGS_FILES,
            activeIndex = HELP_FILE_INDEX,
            onSelect = onSelectFile
        )
        Box(Modifier.weight(1f)) {
            CodeCanvas(
                lines = lines,
                state = listState,
                modifier = Modifier.fillMaxSize()
            )
        }
        TerminalStatusBar {
            StatusBarStart { StatusBarText("⎇ main") }
            // The one file in the app nobody can write to, the reader included:
            // `settings.config` next door says `rw`, and the difference is the
            // point.
            StatusBarText("ro")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 640)
@Composable
private fun HelpScreenPreview() {
    ThabitTheme {
        HelpScreen(onSelectFile = {})
    }
}
