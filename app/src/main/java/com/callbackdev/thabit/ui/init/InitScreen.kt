package com.callbackdev.thabit.ui.init

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.callbackdev.thabit.R
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
 * Since Fase 19 the transcript **prints itself** rather than being already
 * there: see [TypedTranscript] for the two speeds, the tap that ends it and the
 * two accessibility switches that never start it. The four `#` lines above the
 * choices grew with it — a session that takes a second and a half to print can
 * afford to say what the app *is* before saying what it needs, and a still
 * screen could not. They say it in plain words, and the app's own words arrive
 * with a "like a…" in front of them (VISION §3.3.7).
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
    val script = buildInitScript(
        syntax = syntax,
        intro = stringResource(R.string.init_intro),
        files = stringResource(R.string.init_files),
        privacy = stringResource(R.string.init_privacy),
        ask = stringResource(R.string.init_ask),
        add = stringResource(R.string.init_option_add),
        addNote = stringResource(R.string.init_option_add_note),
        skip = stringResource(R.string.init_option_skip),
        skipNote = stringResource(R.string.init_option_skip_note),
        onAddFirstTest = onAddFirstTest,
        onSkip = onSkip
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The insets the workspace has applied since it existed, and this screen
        // never did: it is not a Scaffold and has no nav bar, so the tab strip sat
        // under the clock and the terminal bar under the gesture pill (device,
        // Fase 27b). Same `statusBarsPadding()` as the workspace's own root Column.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            EditorTabs(fileNames = listOf(SETUP_FILE), activeIndex = 0, onSelect = {})
            TypedTranscript(script = script, modifier = Modifier.weight(1f))
            TerminalStatusBar(
                // Bottom-most element of this screen, unlike in the workspace where
                // EditorNavBar is: so it takes the gesture bar's inset the way that
                // bar does — the strip's colour reaches the edge, the text sits above
                // the pill. Painted here because the padding has to be INSIDE the
                // background, and the component applies its own after the modifier.
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .navigationBarsPadding()
            ) {
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
 * The transcript as a pure value, so its shape — and now its timing — can be
 * asserted without a screen.
 *
 * Every `#` note here *is* one of the localized strings, which is the one place
 * in the app where the comment channel is the whole message. That is the same
 * trade the `README.md` tab makes: prose addressed to the reader wins over the
 * fiction of a file written in English.
 *
 * The command is the only line typed at a hand's speed; everything else is the
 * program answering. The beats are where a real session breathes — after the
 * command, and between one offered answer and the next.
 */
internal fun buildInitScript(
    syntax: SyntaxColors,
    intro: String,
    files: String,
    privacy: String,
    ask: String,
    add: String,
    addNote: String,
    skip: String,
    skipNote: String,
    onAddFirstTest: () -> Unit,
    onSkip: () -> Unit
): List<TypedLine> = buildList {
    add(
        typed(
            CodeLine(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.comment)) { append("$ ") }
                    withStyle(SpanStyle(color = syntax.string)) { append("thabit init") }
                }
            )
        )
    )
    add(blank())
    add(printed(comment(intro, syntax)))
    add(printed(comment(files, syntax)))
    add(printed(comment(privacy, syntax)))
    add(printed(comment(ask, syntax), pauseAfterMs = StanzaPauseMs))
    option(add, addNote, syntax, onAddFirstTest)
    option(skip, skipNote, syntax, onSkip)
}

/** `> choice` plus its `#` note: one tap target, and the note says what it costs. */
private fun MutableList<TypedLine>.option(
    label: String,
    note: String,
    syntax: SyntaxColors,
    onClick: () -> Unit
) {
    add(blank())
    add(
        typed(
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
    )
    add(printed(comment(note, syntax, indent = 1), pauseAfterMs = StanzaPauseMs))
}

/**
 * A **prompt** line at a hand's speed, with a beat after it: the `$` command and each
 * `>` answer. They are the turns of the session — the places where somebody types and
 * where the session then waits — and giving them one rhythm is what stops the whole
 * transcript arriving as a single breath after the command (device, Fase 19b/19c).
 */
private fun typed(line: CodeLine): TypedLine =
    TypedLine(line, msPerChar = PromptMsPerChar, pauseAfterMs = PromptPauseMs)

/** Output: the `#` lines the session prints between one turn and the next. */
private fun printed(line: CodeLine, pauseAfterMs: Int = LinePauseMs): TypedLine =
    TypedLine(line, msPerChar = PrintMsPerChar, pauseAfterMs = pauseAfterMs)

private fun comment(text: String, syntax: SyntaxColors, indent: Int = 0): CodeLine =
    CodeLine(AnnotatedString("# $text", SpanStyle(color = syntax.comment)), indent)

private fun blank(): TypedLine = TypedLine(CodeLine(AnnotatedString("")), pauseAfterMs = 0)

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 480)
@Composable
private fun InitScreenPreview() {
    ThabitTheme {
        InitScreen(onAddFirstTest = {}, onSkip = {})
    }
}
