package com.callbackdev.thabit.ui.init

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.thabit.R
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.CodeLine
import com.callbackdev.thabit.ui.components.EditorTabs
import com.callbackdev.thabit.ui.components.StatusBarDivider
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * `$ thabit init` — the first run (Fase 14, the siblings' screen ported).
 *
 * It exists because a fresh thabit has nothing in it and nothing to say: an
 * empty `habits.test` is the honest first frame, but honest is not the same as
 * *inviting*, and the file's own empty state ("tap + to add your first test")
 * reads far better to somebody who chose to be there than to somebody an
 * install dropped there. So the app asks the one question it cannot start
 * without — what is the first habit? — and then gets out of the way.
 *
 * Deliberately not a carousel. Onboarding slides are the most skipped surface
 * in mobile, and a definition offered before you have seen the thing it defines
 * does not stick. The vocabulary lives in `HELP.md`, which is a *file*: it can
 * be re-opened the day the question actually turns up, which is never the day
 * of the install.
 *
 * Two answers, not three — the rule tsteps settled on. A third `> read HELP.md
 * first` would be an option that sends the reader to the manual before the
 * product, and the one-shot hint at the head of `habits.test` already offers it
 * at the moment the file is in front of them.
 *
 * **Localized**, unlike the terminal output everywhere else in the app: the same
 * exception the `README.md` tab already makes (VISION §1.3). The fiction is
 * carried by the shape — the prompt, the `>` choices, the `#` notes — not by the
 * language, and this is the one screen whose whole purpose is being understood
 * by somebody who does not read `git` for a living. `$ thabit init` is a
 * command, so it stays as it is.
 */
@Composable
fun InitScreen(
    onAddFirstTest: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val syntax = ThabitTheme.syntax
    val lines = buildInitLines(
        syntax = syntax,
        intro = stringResource(R.string.init_intro),
        privacy = stringResource(R.string.init_privacy),
        add = stringResource(R.string.init_option_add),
        addNote = stringResource(R.string.init_option_add_note),
        skip = stringResource(R.string.init_option_skip),
        skipNote = stringResource(R.string.init_option_skip_note),
        onAddFirstTest = onAddFirstTest,
        onSkip = onSkip
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(fileNames = listOf(SETUP_FILE), activeIndex = 0, onSelect = {})
            CodeCanvas(
                lines = lines,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                // A transcript has no nesting: the `#` note under each choice is
                // indented one level to belong to it, and a guide rail drawn down
                // the middle of a two-line answer would be reading structure into
                // a conversation.
                showIndentGuides = false
            )
            TerminalStatusBar {
                StatusBarStart { StatusBarText("⎇ setup") }
                StatusBarDivider()
                StatusBarText("1/1")
            }
        }
    }
}

/**
 * The "file" this screen opens: a session, not a document — hence the shell
 * name, and hence the one tab in the app whose file is never seen again.
 */
internal const val SETUP_FILE: String = "thabit.sh"

/**
 * The transcript as a pure value, so its shape can be asserted without a screen.
 *
 * Every `#` note here *is* one of the localized strings, which is the one place
 * in the app where the comment channel is the whole message. That is the same
 * trade the `README.md` tab makes: prose addressed to the reader wins over the
 * fiction of a file written in English.
 */
internal fun buildInitLines(
    syntax: SyntaxColors,
    intro: String,
    privacy: String,
    add: String,
    addNote: String,
    skip: String,
    skipNote: String,
    onAddFirstTest: () -> Unit,
    onSkip: () -> Unit
): List<CanvasLine> = buildList {
    add(
        CodeLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                withStyle(SpanStyle(color = syntax.string)) { append("thabit init") }
            }
        )
    )
    add(blank())
    add(comment(intro, syntax))
    add(comment(privacy, syntax))
    option(add, addNote, syntax, onAddFirstTest)
    option(skip, skipNote, syntax, onSkip)
}

/** `> choice` plus its `#` note: one tap target, and the note says what it costs. */
private fun MutableList<CanvasLine>.option(
    label: String,
    note: String,
    syntax: SyntaxColors,
    onClick: () -> Unit
) {
    add(blank())
    add(
        CodeLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("> ") }
                withStyle(SpanStyle(color = syntax.key)) { append(label) }
            },
            onClick = onClick,
            onClickLabel = label,
            // The `>` is a prompt, not a word: a screen reader gets the answer.
            contentDescription = label
        )
    )
    add(comment(note, syntax, indent = 1))
}

private fun comment(text: String, syntax: SyntaxColors, indent: Int = 0): CodeLine =
    CodeLine(AnnotatedString("# $text", SpanStyle(color = syntax.comment)), indent)

private fun blank(): CodeLine = CodeLine(AnnotatedString(""))

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 480)
@Composable
private fun InitScreenPreview() {
    ThabitTheme {
        InitScreen(onAddFirstTest = {}, onSkip = {})
    }
}
