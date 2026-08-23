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
    val alert: Int,
    /** A `[x]`: the same green the file gives a passed test. */
    val pass: Int
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
        TokenRole.PASS -> pass
    }
}

fun widgetPalette(profileName: String): WidgetPalette = when (ThemeProfile.fromName(profileName)) {
    ThemeProfile.Dracula -> palette(
        DraculaColors.background, DraculaColors.secondary, DraculaColors.onSurface, DraculaSyntax
    )
    ThemeProfile.Monokai -> palette(
        MonokaiColors.background, MonokaiColors.secondary, MonokaiColors.onSurface, MonokaiSyntax
    )
    else -> palette(
        ObsidianColors.background, ObsidianColors.secondary, ObsidianColors.onSurface, ObsidianSyntax
    )
}

private fun palette(
    background: Color,
    secondary: Color,
    onSurface: Color,
    syntax: SyntaxColors
) = WidgetPalette(
    background = background.toArgb(),
    border = syntax.border.toArgb(),
    title = onSurface.toArgb(),
    // The prompt's green is the series' green, so `you@thabit` matches
    // `you@tsteps` and `you@tweather` on the same home screen.
    prompt = secondary.toArgb(),
    plain = onSurface.toArgb(),
    key = syntax.key.toArgb(),
    string = syntax.string.toArgb(),
    number = syntax.number.toArgb(),
    comment = syntax.comment.toArgb(),
    alert = syntax.diffDel.toArgb(),
    // A `[x]` is green everywhere in this app, so it is that green here too:
    // the widget's rows are the same rows, not a second visual language.
    pass = syntax.diffAdd.toArgb()
)
