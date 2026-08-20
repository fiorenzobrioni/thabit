package com.callbackdev.thabit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * YAML → syntax-highlighted [CodeLine]s for `habits_test.yaml` — the first tokenizer
 * born in this app rather than ported. Like the siblings' builders it is not a
 * parser: the document is composed by hand, one honest line at a time.
 *
 * Token colors ([SyntaxColors]): keys blue, scalar values light blue, numbers
 * orange, list dashes and punctuation gray. The checkbox is thabit's own token —
 * `[x]` diff-green, `[ ]` neutral (inherits the canvas' on-surface), `[~]` comment
 * gray, `[!]` diff-red.
 *
 * Comment rule (VISION §1.1): inside YAML the comment channel is `#`, never `//` —
 * the comment wears the host file's syntax. Full-line comments go through
 * [commentLine] with a `#` marker; trailing live-detail comments (`# 07:12`,
 * `# 2/3 this week`) are dimmed like the siblings' inline hints.
 */

/** The four states a test's checkbox can render. */
enum class CheckboxState(val glyph: String) {
    Passed("[x]"),
    Pending("[ ]"),
    Skipped("[~]"),
    Failed("[!]");

    /** Token color; null = neutral (the canvas' default on-surface). */
    fun color(syntax: SyntaxColors): Color? = when (this) {
        Passed -> syntax.diffAdd
        Pending -> null
        Skipped -> syntax.comment
        Failed -> syntax.diffDel
    }
}

/** The bare checkbox token, colored by state — for widget rows and custom lines. */
fun checkboxToken(state: CheckboxState, syntax: SyntaxColors): AnnotatedString =
    buildAnnotatedString {
        val color = state.color(syntax)
        if (color != null) {
            withStyle(SpanStyle(color = color)) { append(state.glyph) }
        } else {
            append(state.glyph)
        }
    }

/**
 * One test of the suite: `- [x] meditate 10 min  # 07:12`. The dash is list
 * punctuation (gray), the checkbox carries its state color, the name stays
 * unspanned (on-surface — it is user data, emoji included), and the trailing
 * `#` comment is the dimmed live-detail channel. With an [onClick] the whole
 * line is the tap target (Fase 3 wires per-token controls as [WidgetLine]s).
 */
fun yamlTestLine(
    state: CheckboxState,
    name: String,
    syntax: SyntaxColors,
    indent: Int = 0,
    comment: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.comment)) { append("- ") }
        append(checkboxToken(state, syntax))
        append(" $name")
        appendYamlComment(comment, syntax)
    },
    indent = indent,
    onClick = onClick,
    onClickLabel = onClickLabel
)

/**
 * `when: daily` — a key/scalar line of a test's expanded spec. Quoted values keep
 * their quotes inside the string color (`remind: "07:00"`), like real YAML.
 */
fun yamlStringLine(
    key: String,
    value: String,
    syntax: SyntaxColors,
    indent: Int = 0,
    comment: String? = null,
    quoted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
): CodeLine = yamlKeyValueLine(
    key = key,
    value = if (quoted) "\"$value\"" else value,
    valueColor = syntax.string,
    syntax = syntax,
    indent = indent,
    comment = comment,
    onClick = onClick,
    onClickLabel = onClickLabel
)

/** `streak: 18` — numeric/boolean scalars in the number color. */
fun yamlNumberLine(
    key: String,
    value: String,
    syntax: SyntaxColors,
    indent: Int = 0,
    comment: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
): CodeLine = yamlKeyValueLine(
    key = key,
    value = value,
    valueColor = syntax.number,
    syntax = syntax,
    indent = indent,
    comment = comment,
    onClick = onClick,
    onClickLabel = onClickLabel
)

private fun yamlKeyValueLine(
    key: String,
    value: String,
    valueColor: Color,
    syntax: SyntaxColors,
    indent: Int,
    comment: String?,
    onClick: (() -> Unit)?,
    onClickLabel: String?
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.key)) { append(key) }
        withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
        withStyle(SpanStyle(color = valueColor)) { append(value) }
        appendYamlComment(comment, syntax)
    },
    indent = indent,
    onClick = onClick,
    onClickLabel = onClickLabel
)

/** Trailing `  # detail`, dimmed like the siblings' inline hints. */
private fun AnnotatedString.Builder.appendYamlComment(comment: String?, syntax: SyntaxColors) {
    if (comment == null) return
    withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) { append("  # $comment") }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 320)
@Composable
private fun YamlSyntaxPreview() {
    ThabitTheme {
        val syntax = ThabitTheme.syntax
        val lines = remember(syntax) {
            listOf(
                commentLine("# habits_test.yaml", syntax),
                commentLine("# suite 2026-08-20 — 3 passed · 2 pending · 1 skipped", syntax),
                yamlTestLine(CheckboxState.Passed, "meditate 10 min", syntax, comment = "07:12"),
                yamlTestLine(CheckboxState.Passed, "read 20 pages 📖", syntax, comment = "23 pages"),
                yamlTestLine(CheckboxState.Pending, "pushups", syntax, comment = "12/30    [+1]"),
                yamlTestLine(CheckboxState.Skipped, "run 5k", syntax, comment = "skip: rest day"),
                yamlTestLine(CheckboxState.Failed, "no sugar", syntax, comment = "failed 16:40"),
                yamlStringLine("when", "daily", syntax, indent = 1),
                yamlStringLine("remind", "07:00", syntax, indent = 1, quoted = true),
                yamlNumberLine("streak", "18", syntax, indent = 1, comment = "days")
            )
        }
        CodeCanvas(lines = lines)
    }
}
