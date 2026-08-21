package com.callbackdev.thabit.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.callbackdev.thabit.ui.components.CheckboxState
import com.callbackdev.thabit.ui.theme.SyntaxColors

/**
 * One tappable test line: `- [x] meditate 10 min  # 07:12   [+1]`.
 *
 * It cannot be a plain [com.callbackdev.thabit.ui.components.CodeLine] because
 * the line carries **two** different gestures (VISION §4.1): the checkbox runs
 * the test, the name unfolds its spec. One `onClick` for the whole row would
 * have meant choosing which of the two to lose.
 *
 * Accessibility is split the same way instead of merging the row into one
 * announcement: the checkbox speaks the whole fact (*meditate 10 min, passed,
 * at 07:12*) and the name offers the details — so a screen reader gets two
 * targets that each say what they do, and never reads "left bracket x right
 * bracket" (VISION §3.3.7).
 */
@Composable
fun TestLine(
    checkbox: CheckboxState,
    name: String,
    comment: String?,
    syntax: SyntaxColors,
    spokenRow: String,
    checkboxActionLabel: String,
    detailsDescription: String,
    detailsActionLabel: String,
    onCheckbox: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    incrementLabel: String? = null,
    incrementDescription: String? = null,
    onIncrement: (() -> Unit)? = null,
    noteLabel: String? = null,
    noteDescription: String? = null,
    onNote: (() -> Unit)? = null
) {
    val style = MaterialTheme.typography.bodySmall
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // `- [x] ` — dash, box and the space after it are one target, six
        // monospace columns wide (about 47dp at the code style's 13sp), which is
        // a comfortable target for the day's most frequent gesture.
        //
        // The width comes from the text itself and **not** from horizontal
        // padding, and that is the whole trick: padding before the dash would
        // shift the row against the `#` comment lines above it, and a column
        // that does not line up is the one thing a code editor may never do.
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("- ") }
                val color = checkbox.color(syntax)
                if (color != null) {
                    withStyle(SpanStyle(color = color)) { append(checkbox.glyph) }
                } else {
                    append(checkbox.glyph)
                }
                append(" ")
            },
            style = style,
            modifier = Modifier
                .clickable(onClickLabel = checkboxActionLabel, onClick = onCheckbox)
                .padding(vertical = RowTouchPadding)
                .semantics { contentDescription = spokenRow }
        )
        Text(
            text = name,
            style = style,
            modifier = Modifier
                .clickable(onClickLabel = detailsActionLabel, onClick = onDetails)
                .padding(vertical = RowTouchPadding)
                .semantics { contentDescription = detailsDescription }
        )
        if (comment != null) {
            Text(
                text = "  # $comment",
                style = style,
                color = syntax.comment.copy(alpha = 0.6f),
                modifier = Modifier.decorative()
            )
        }
        if (incrementLabel != null && onIncrement != null) {
            TextControl(
                label = incrementLabel,
                color = syntax.number,
                description = incrementDescription ?: incrementLabel,
                onClick = onIncrement
            )
        }
        if (noteLabel != null && onNote != null) {
            TextControl(
                label = noteLabel,
                color = syntax.comment,
                description = noteDescription ?: noteLabel,
                onClick = onNote
            )
        }
    }
}

/**
 * Marks a piece of the file as **look, not meaning** — invisible to a screen
 * reader, untouched on screen.
 *
 * The comment channel is source (VISION §1.3): `# 12/30 reps` is English by
 * design and is written for the eye. Every fact it carries is already spoken,
 * localized, by the control it sits next to — the checkbox says *12 di 30
 * pagine*, the `[3]` token says *3 volte a settimana*. Left in the accessibility
 * tree those comments are read a second time, in a language the listener did not
 * choose, with the punctuation spelled out: the exact "left bracket dot right
 * bracket" noise VISION §3.3.7 forbids, arriving through the other door.
 *
 * The rule this encodes: **a screen reader hears each fact once**, from whatever
 * the reader can actually tap.
 *
 * `hideFromAccessibility` and not `clearAndSetSemantics`: the first hides the
 * line from accessibility services and leaves the node where it is, so the words
 * of the file stay selectable on screen and stay **assertable in the tests** —
 * and the tests asserting the file character by character is the habit this
 * project is built on. Clearing the semantics would have hidden the comments
 * from the suite that guards them too.
 */
fun Modifier.decorative(): Modifier = semantics { hideFromAccessibility() }

/**
 * Vertical breathing room on the two tap targets of a row.
 *
 * Four points either side of a 22sp line gives a row of about 30dp: short of
 * Material's 48dp, and deliberately so — this is a dense text file, and rows tall
 * enough to satisfy that number would fit four tests on a screen instead of ten,
 * costing the app the one glance it is built around (VISION §3.3.2). The
 * horizontal target is the comfortable half; whether the vertical one needs more
 * is a question for a real thumb, so it is on the Fase 12 list.
 */
private val RowTouchPadding = 4.dp

/**
 * A control rendered as text: `[+1]`, `[~ skip]`, `[esc]`.
 *
 * The series has no buttons — a control is a token in the file, and the only
 * thing that tells it apart from the surrounding source is that it answers to a
 * tap (VISION §1.1). The spoken name is what it *does*, never the glyph.
 */
@Composable
fun TextControl(
    label: String,
    color: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier
            .clickable(onClickLabel = actionLabel, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = RowTouchPadding)
            .semantics { contentDescription = description }
    )
}
