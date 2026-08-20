package com.callbackdev.thabit.ui.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.callbackdev.thabit.ui.theme.ThabitTheme

/**
 * Fase 0 placeholder: a static, hand-colored `habits_test.yaml` with a line-number
 * gutter, so the first installable build already looks like thabit. The real editor
 * kit (CodeCanvas, tabs, status bar) arrives in Fase 1 and replaces this file.
 */
@Composable
fun SkeletonScreen() {
    val syntax = ThabitTheme.syntax
    val onSurface = MaterialTheme.colorScheme.onSurface

    fun comment(text: String): AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.comment)) { append(text) }
    }

    fun test(box: String, boxColor: Color, name: String, trailing: String) =
        buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.comment)) { append("- ") }
            withStyle(SpanStyle(color = boxColor)) { append(box) }
            withStyle(SpanStyle(color = onSurface)) { append(" $name") }
            if (trailing.isNotEmpty()) {
                withStyle(SpanStyle(color = syntax.comment)) { append("  $trailing") }
            }
        }

    val lines = listOf(
        comment("# habits_test.yaml"),
        comment("# suite 2026-08-20 — 3 passed · 2 pending · 1 skipped"),
        AnnotatedString(""),
        test("[x]", syntax.diffAdd, "meditate 10 min", "# 07:12"),
        test("[x]", syntax.diffAdd, "read 20 pages 📖", "# 23 pages"),
        test("[ ]", onSurface, "pushups", "# 12/30    [+1]"),
        test("[~]", syntax.comment, "run 5k", "# skip: rest day"),
        test("[ ]", onSurface, "no sugar", "# holds — asserts at commit"),
        test("[x]", syntax.diffAdd, "journal", "# 21:40"),
        AnnotatedString(""),
        comment("# not yet written — the suite arrives with Fase 3")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(vertical = 12.dp)
    ) {
        lines.forEachIndexed { index, line ->
            Row {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = syntax.comment.copy(alpha = 0.6f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(40.dp)
                        .padding(end = 12.dp)
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
