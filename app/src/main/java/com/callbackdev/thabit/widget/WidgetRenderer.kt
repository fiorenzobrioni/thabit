package com.callbackdev.thabit.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.callbackdev.thabit.MainActivity
import com.callbackdev.thabit.R
import com.callbackdev.thabit.notifications.CheckActionReceiver

/**
 * Builds the RemoteViews for one render pass — the series' renderer, inherited
 * whole, plus the one thing thabit's widget has that the siblings' do not:
 * **every body line is its own control**.
 *
 * [sizeMap] returns the API 31+ sizes-map RemoteViews, so the launcher picks the
 * best-fitting tier itself on every resize with no round-trip to the provider.
 * Per-token colors travel as [ForegroundColorSpan]s — a ParcelableSpan, safe
 * across the RemoteViews IPC; fonts stay in the XML, because typeface spans do
 * not parcel (which is why the widget is system `monospace` and not JetBrains
 * Mono — CVE-2021-0567 closed that door series-wide).
 */
object WidgetRenderer {

    private val LineIds = listOf(
        R.id.widget_line1, R.id.widget_line2, R.id.widget_line3,
        R.id.widget_line4, R.id.widget_line5, R.id.widget_line6,
        R.id.widget_line7, R.id.widget_line8, R.id.widget_line9,
        R.id.widget_line10
    )

    /** Slots the compact layout carries; taller transcripts use the large one. */
    private const val MediumSlots = 4

    // Measured, not estimated (the series' rule): the renderer test
    // binary-searches the real laid-out minimum of every rung and fails if these
    // drift. They carry a deliberate margin over that measurement for the OEM's
    // own monospace font — one line fewer is a far better failure than one line
    // sliced. thabit's body line is taller than the siblings' because here a line
    // is a **tap target**: a 23dp row is fine to read and mean to hit.
    private const val ChromeHeightDp = 64f
    private const val BodyLineHeightDp = 30f
    private const val SmallMinHeightDp = 52f

    fun sizeMap(
        context: Context,
        content: (WidgetTier) -> WidgetContent,
        palette: WidgetPalette,
        opacityPct: Int
    ): RemoteViews = RemoteViews(
        breakpoints().entries.associate { (tier, size) ->
            size to render(context, content(tier), palette, opacityPct, tier)
        }
    )

    /**
     * A sizes-map key is a promise that the layout FITS in that many dp — the
     * host clips silently otherwise. Chrome (title bar, divider, prompt, bottom
     * padding) plus one line per slot. Below the smallest key the launcher falls
     * back to it, so a minimum-size widget still gets the glanceable strip.
     */
    internal fun minHeightDp(lines: Int): Float = ChromeHeightDp + lines * BodyLineHeightDp

    /**
     * One rung per transcript line, and none past
     * [WidgetContentBuilder.MAX_LINES]: a rung nothing can fill just promises
     * height it never uses.
     *
     * The terminal rungs start wider than the siblings' 160dp because thabit's
     * always-visible first line carries a date **and** the suite arithmetic
     * (`2026-08-21  ▓▓▓▓▓░░░░░ 3/6`), and a line that ellipsizes away its own
     * numbers would be the widget losing its one fact. Narrower than that, the
     * launcher falls back to the glanceable strip, which states the same two
     * things with room to spare.
     */
    internal fun breakpoints(): Map<WidgetTier, SizeF> = buildMap {
        put(WidgetTier.Small, SizeF(110f, SmallMinHeightDp))
        (2..WidgetContentBuilder.MAX_LINES).forEach { lines ->
            put(WidgetTier.Terminal(lines), SizeF(230f, minHeightDp(lines)))
        }
    }

    internal fun render(
        context: Context,
        content: WidgetContent,
        palette: WidgetPalette,
        opacityPct: Int,
        tier: WidgetTier
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutFor(tier))

        views.setInt(R.id.widget_bg_fill, "setColorFilter", palette.background)
        // setImageAlpha masks with 0xFF instead of clamping — never hand it an
        // out-of-range value.
        views.setInt(R.id.widget_bg_fill, "setImageAlpha", (opacityPct * 255 / 100).coerceIn(0, 255))
        views.setInt(R.id.widget_bg_border, "setColorFilter", palette.border)

        views.setTextViewText(R.id.widget_emoji, content.emoji ?: "")
        views.setViewVisibility(
            R.id.widget_emoji,
            if (content.emoji != null) View.VISIBLE else View.GONE
        )

        if (tier is WidgetTier.Small) {
            views.setTextViewText(R.id.widget_small_value, content.smallValue.spannable(palette))
            views.setTextViewText(R.id.widget_small_label, content.smallLabel.spannable(palette))
            views.setContentDescription(R.id.widget_small_value, content.spokenSummary)
        } else {
            views.setTextViewText(R.id.widget_title, content.headerTitle)
            views.setTextColor(R.id.widget_title, palette.title)
            views.setInt(R.id.widget_divider, "setBackgroundColor", palette.divider)
            views.setInt(R.id.widget_guide, "setBackgroundColor", palette.divider)
            views.setTextViewText(R.id.widget_prompt, content.promptLine.spannable(palette))

            LineIds.take(slotsFor(tier)).forEachIndexed { index, id ->
                val line = content.bodyLines.getOrNull(index)
                if (line == null) {
                    views.setViewVisibility(id, View.GONE)
                    return@forEachIndexed
                }
                views.setTextViewText(id, line.spannable(palette))
                views.setViewVisibility(id, View.VISIBLE)
                // The row's own words, so a screen reader never meets a glyph —
                // and on the widget that is not a nicety: there is no `#` comment
                // here to tell `[·]` from `[ ]` (VISION §3.3.7, §4.6).
                line.spoken?.let { views.setContentDescription(id, it) }
                views.setOnClickPendingIntent(id, intentFor(context, line.action))
            }
        }

        // The root stays tappable for everything that is not a row: the padding,
        // the title bar, the prompt.
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, habitId = null))
        return views
    }

    /** Slots this tier binds — never more than its layout carries. */
    private fun slotsFor(tier: WidgetTier): Int =
        minOf(WidgetContentBuilder.bodyLineBudget(tier), LineIds.size)

    internal fun layoutFor(tier: WidgetTier): Int = when {
        tier is WidgetTier.Small -> R.layout.widget_thabit_small
        WidgetContentBuilder.bodyLineBudget(tier) <= MediumSlots -> R.layout.widget_thabit_medium
        else -> R.layout.widget_thabit_large
    }

    private fun WidgetLine.spannable(palette: WidgetPalette): CharSequence =
        SpannableStringBuilder().apply {
            tokens.forEach { token ->
                val start = length
                append(token.text)
                setSpan(
                    ForegroundColorSpan(palette.colorFor(token.role)),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

    /**
     * The PendingIntent a line's action needs.
     *
     * Both branches carry a **distinct request code and a distinct data Uri**,
     * the series' `filterEquals` lesson: PendingIntent comparison ignores extras
     * entirely, so six rows differing only by a habit id would collapse into one
     * intent and every row would check off the first test.
     */
    private fun intentFor(context: Context, action: WidgetAction): PendingIntent = when (action) {
        is WidgetAction.Pass -> PendingIntent.getBroadcast(
            context,
            requestCode(action.habitId),
            Intent(context, CheckActionReceiver::class.java)
                .setAction(CheckActionReceiver.ACTION_PASS)
                .putExtra(MainActivity.EXTRA_HABIT_ID, action.habitId)
                .setData(Uri.parse("thabit://widget/pass/${action.habitId}")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        is WidgetAction.Open -> openAppIntent(context, action.habitId)
        WidgetAction.OpenApp -> openAppIntent(context, habitId = null)
    }

    private fun openAppIntent(context: Context, habitId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            // SINGLE_TOP is what makes CLEAR_TOP resume the running activity:
            // without it MainActivity is finished and rebuilt, replaying the
            // splash (the series' lesson).
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        habitId?.let {
            intent.putExtra(MainActivity.EXTRA_HABIT_ID, it)
            intent.data = Uri.parse("thabit://widget/open/$it")
        }
        return PendingIntent.getActivity(
            context,
            habitId?.let { requestCode(it) } ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Widget request codes live above the reminders' block, so nothing collides. */
    private fun requestCode(habitId: Long): Int = (100_000 + habitId).toInt()
}
