package com.callbackdev.thabit.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.callbackdev.thabit.ui.theme.DraculaColors
import com.callbackdev.thabit.ui.theme.DraculaSyntax
import com.callbackdev.thabit.ui.theme.MonokaiColors
import com.callbackdev.thabit.ui.theme.MonokaiSyntax
import com.callbackdev.thabit.ui.theme.ObsidianColors
import com.callbackdev.thabit.ui.theme.ObsidianSyntax
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThemeProfile

/**
 * The theme tokens a widget render needs, as ARGB ints (RemoteViews knows no
 * Compose Color). Ported from the siblings; the one thing thabit changes is what
 * `PROMPT` is used for — here it colours a passed test and the suite bar, so it
 * is the profile's green, the same one `diffAdd` gives a `[x]` in the file.
 */
data class WidgetPalette(
    val background: Int,
    val border: Int,
    val title: Int,
    val prompt: Int,
    val plain: Int,
    val key: Int,
    val string: Int,
    val number: Int,
    val comment: Int,
    val alert: Int
) {
    val divider: Int get() = border

    fun colorFor(role: TokenRole): Int = when (role) {
        TokenRole.PROMPT -> prompt
        TokenRole.PLAIN -> plain
        TokenRole.DIM -> comment
        TokenRole.KEY -> key
        TokenRole.STRING -> string
        TokenRole.NUMBER -> number
        TokenRole.COMMENT -> comment
        TokenRole.ALERT -> alert
    }
}

fun widgetPalette(profileName: String): WidgetPalette = when (ThemeProfile.fromName(profileName)) {
    ThemeProfile.Dracula ->
        palette(DraculaColors.background, DraculaColors.onSurface, DraculaSyntax)
    ThemeProfile.Monokai ->
        palette(MonokaiColors.background, MonokaiColors.onSurface, MonokaiSyntax)
    else -> palette(ObsidianColors.background, ObsidianColors.onSurface, ObsidianSyntax)
}

private fun palette(
    background: Color,
    onSurface: Color,
    syntax: SyntaxColors
) = WidgetPalette(
    background = background.toArgb(),
    border = syntax.border.toArgb(),
    title = onSurface.toArgb(),
    // A `[x]` is green everywhere in this app, so it is green here too — the
    // widget's rows are the same rows, not a second visual language.
    prompt = syntax.diffAdd.toArgb(),
    plain = onSurface.toArgb(),
    key = syntax.key.toArgb(),
    string = syntax.string.toArgb(),
    number = syntax.number.toArgb(),
    comment = syntax.comment.toArgb(),
    alert = syntax.diffDel.toArgb()
)
