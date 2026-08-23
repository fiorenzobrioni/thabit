package com.callbackdev.thabit.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.MainActivity
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.notifications.CheckActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

/**
 * The renderer is only observable through the view tree the launcher would get,
 * so every test inflates the RemoteViews for real (`apply`) and asserts on the
 * resulting Views — which also proves the layouts stay RemoteViews-compatible.
 * The measuring tests are the series': breakpoints get measured, never trusted.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRendererTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val palette = widgetPalette("Obsidian")
    private val today = LocalDate.of(2026, 8, 21)

    private fun data() = WidgetData(
        date = today,
        outcomes = listOf(
            TestOutcome(Fixture.habit(id = 1L, name = "meditate 10 min"), TestState.PASS),
            TestOutcome(Fixture.habit(id = 2L, name = "read 20 pages"), TestState.PENDING),
            TestOutcome(Fixture.habit(id = 3L, name = "pushups"), TestState.PENDING),
            TestOutcome(
                Fixture.habit(id = 4L, name = "no sugar", type = HabitType.AVOID),
                TestState.HOLDING
            ),
            TestOutcome(Fixture.habit(id = 5L, name = "journal"), TestState.PENDING),
            TestOutcome(Fixture.habit(id = 6L, name = "run 5k"), TestState.PENDING),
            TestOutcome(Fixture.habit(id = 7L, name = "water"), TestState.PENDING),
            TestOutcome(Fixture.habit(id = 8L, name = "stretch"), TestState.PENDING)
        ),
        suiteSize = 8
    )

    private fun content(tier: WidgetTier, data: WidgetData = data()) =
        WidgetContentBuilder.build(data, tier, context.resources)

    private fun inflate(
        content: WidgetContent,
        tier: WidgetTier,
        opacityPct: Int = 100
    ): View = WidgetRenderer.render(context, content, palette, opacityPct, tier)
        .apply(context, FrameLayout(context))

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun View.visibility(id: Int): Int = findViewById<View>(id).visibility

    /** Color of the token covering [index] — spans are the only carrier of color. */
    private fun View.tokenColorAt(id: Int, index: Int): Int {
        val text = findViewById<TextView>(id).text as Spanned
        return text.getSpans(index, index + 1, ForegroundColorSpan::class.java)
            .single()
            .foregroundColor
    }

    // ---- what each tier binds --------------------------------------------

    @Test
    fun mediumBindsTitlePromptAndFourBodyLines() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))
        assertEquals(WidgetContentBuilder.HEADER, view.text(R.id.widget_title))
        assertEquals("you@thabit:~$ cat habits.test", view.text(R.id.widget_prompt))
        assertTrue(view.text(R.id.widget_line1).startsWith("Suite: 1/8 "))
        assertEquals("[x] meditate 10 min", view.text(R.id.widget_line2))
        assertEquals(View.VISIBLE, view.visibility(R.id.widget_line4))
    }

    @Test
    fun theLargeTierBindsTheWholeSuiteAndHidesTheSlotsItDoesNotFill() {
        val tier = WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)
        val view = inflate(content(tier), tier)
        // suite line + eight rows + the trailing comment fills all ten slots;
        // the holding avoid test is not counted as pending.
        assertEquals("[·] no sugar", view.text(R.id.widget_line5))
        assertEquals("# 2026-08-21 · 6 pending — tap to pass", view.text(R.id.widget_line10))

        // ...and a shorter suite leaves the tail of the slots hidden.
        val short = WidgetData(date = today, outcomes = data().outcomes.take(2), suiteSize = 2)
        val small = inflate(content(tier, short), tier)
        assertEquals(View.GONE, small.visibility(R.id.widget_line5))
    }

    /**
     * The siblings' title bar sits between two 48dp boxes — theirs is a refresh
     * control, thabit's is empty — and that is what makes the title read
     * centred. Dropping the box left the title against the left edge and the
     * three widgets stopped looking like a set on one home screen.
     */
    @Test
    fun theTitleIsCentredTheWayTheSiblingWidgetsCentreTheirs() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))
        val title = view.findViewById<TextView>(R.id.widget_title)
        assertTrue(
            "the title is not centred: ${title.gravity}",
            title.gravity and Gravity.CENTER_HORIZONTAL == Gravity.CENTER_HORIZONTAL
        )
        // Its left edge has to clear the balancing box, or "centred" is a lie.
        assertTrue("nothing balances the emoji box", title.left >= 0)
    }

    @Test
    fun theSmallTierBindsTheGlanceableStrip() {
        val view = inflate(content(WidgetTier.Small), WidgetTier.Small)
        assertEquals("1/8", view.text(R.id.widget_small_value))
        assertEquals("2026-08-21", view.text(R.id.widget_small_label))
    }

    // ---- colors cross the IPC as spans ------------------------------------

    @Test
    fun everyTokenKeepsItsColorAcrossTheRemoteViewsBoundary() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))
        // `[x]` wears the file's own green, which is NOT the prompt's green:
        // the prompt is the series' secondary, so `you@thabit` matches
        // `you@tsteps` on the same home screen.
        assertEquals(palette.pass, view.tokenColorAt(R.id.widget_line2, 1))
        assertNotEquals(palette.pass, palette.prompt)
        assertEquals(palette.prompt, view.tokenColorAt(R.id.widget_prompt, 1))
        // the name is plain on-surface, whatever the state
        assertEquals(palette.plain, view.tokenColorAt(R.id.widget_line2, 5))
        // the field name is key blue, like every sibling widget's fields
        assertEquals(palette.key, view.tokenColorAt(R.id.widget_line1, 0))
        assertEquals(palette.number, view.tokenColorAt(R.id.widget_line1, 7))
    }

    @Test
    fun aHoldingAvoidTestIsDimmedApartFromAPendingOne() {
        val view = inflate(
            content(WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)),
            WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)
        )
        assertEquals("[·] no sugar", view.text(R.id.widget_line5))
        assertEquals(palette.comment, view.tokenColorAt(R.id.widget_line5, 1))
        assertEquals(palette.plain, view.tokenColorAt(R.id.widget_line3, 1))
    }

    // ---- the rows are controls -------------------------------------------

    @Test
    fun anUntouchedBooleanChecksOffWithoutOpeningTheApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))
        shadowOf(app).clearBroadcastIntents()

        // line3 is `[ ] read 20 pages`
        view.findViewById<View>(R.id.widget_line3).performClick()

        val sent = shadowOf(app).broadcastIntents.last()
        assertEquals(CheckActionReceiver.ACTION_PASS, sent.action)
        assertEquals(2L, sent.getLongExtra(MainActivity.EXTRA_HABIT_ID, -1L))
    }

    @Test
    fun twoRowsAreTwoIntents_notOneCheckingOffTheFirstTestSixTimes() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val view = inflate(
            content(WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)),
            WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)
        )
        shadowOf(app).clearBroadcastIntents()

        view.findViewById<View>(R.id.widget_line3).performClick()
        view.findViewById<View>(R.id.widget_line6).performClick()

        // `filterEquals` ignores extras, so without distinct request codes AND
        // distinct data Uris both rows would fire the same intent.
        val ids = shadowOf(app).broadcastIntents.map {
            it.getLongExtra(MainActivity.EXTRA_HABIT_ID, -1L)
        }
        assertEquals(listOf(2L, 5L), ids)
        assertNotEquals(
            shadowOf(app).broadcastIntents[0].data,
            shadowOf(app).broadcastIntents[1].data
        )
    }

    @Test
    fun aCounterOrAnAvoidTestOpensTheAppOnItsOwnRow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val view = inflate(
            content(WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)),
            WidgetTier.Terminal(WidgetContentBuilder.MAX_LINES)
        )
        // line5 is `[·] no sugar` — an avoid test's only widget verb would be
        // "I broke it", and that is not a home-screen gesture.
        view.findViewById<View>(R.id.widget_line5).performClick()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(4L, started.getLongExtra(MainActivity.EXTRA_HABIT_ID, -1L))
    }

    @Test
    fun everyRowSpeaksItsOwnWordsToAScreenReader() {
        val view = inflate(content(WidgetTier.Terminal(4)), WidgetTier.Terminal(4))
        val spoken = view.findViewById<View>(R.id.widget_line3).contentDescription.toString()
        assertEquals("read 20 pages, still to do. Tap to mark it done", spoken)
        assertTrue(!spoken.contains("["))
    }

    // ---- the ladder -------------------------------------------------------

    /**
     * A sizes-map key promises the layout FITS at that size — the host clips
     * silently otherwise, so the breakpoints get measured, not trusted.
     */
    @Test
    fun everyTierFitsInsideItsOwnBreakpoint() {
        val density = context.resources.displayMetrics.density

        WidgetRenderer.breakpoints().forEach { (tier, size) ->
            val root = inflate(content(tier), tier)
            val widthPx = (size.width * density).toInt()
            val heightPx = (size.height * density).toInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, widthPx, heightPx)

            val visible = (if (tier == WidgetTier.Small) {
                listOf(R.id.widget_small_value, R.id.widget_small_label)
            } else {
                listOf(R.id.widget_title, R.id.widget_prompt) + (1..4).mapNotNull { slot ->
                    context.resources.getIdentifier("widget_line$slot", "id", context.packageName)
                        .takeIf { it != 0 }
                }
            }).mapNotNull { root.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }

            visible.forEach { line ->
                val bottom = IntArray(2).also { line.getLocationInWindow(it) }[1] + line.height
                assertTrue(
                    "$tier clips a line at its own ${size.width}x${size.height}dp breakpoint",
                    bottom <= heightPx
                )
            }
        }
    }

    /**
     * A rung taller than its transcript needs is not harmless: the launcher only
     * picks a rung that fits, so every wasted dp is a line the user paid for in
     * screen space and did not get. Binary-search the real minimum and hold the
     * promised height close to it (the series' test).
     */
    @Test
    fun noRungClaimsMoreHeightThanItsTranscriptNeeds() {
        val density = context.resources.displayMetrics.density
        val widthPx = (230 * density).toInt()

        val slack = WidgetRenderer.breakpoints()
            .filterKeys { it is WidgetTier.Terminal }
            .toSortedMap(compareBy { WidgetContentBuilder.bodyLineBudget(it) })
            .map { (tier, size) ->
                val lines = WidgetContentBuilder.bodyLineBudget(tier)
                var low = 0
                var high = (600 * density).toInt()
                while (low < high) {
                    val mid = (low + high) / 2
                    if (fitsAt(tier, widthPx, mid, lines)) high = mid else low = mid + 1
                }
                Triple(lines, low / density, size.height)
            }

        val wrong = slack.filter { (lines, needed, promised) ->
            promised < needed || promised - needed > 8f + 3f * lines
        }
        assertTrue(
            "rungs out of step (lines, needed dp, promised dp): $wrong — all: $slack",
            wrong.isEmpty()
        )
    }

    /**
     * The always-visible line carries the date AND the arithmetic, so the
     * narrowest terminal rung has to be wide enough to print it whole: a rung
     * that ellipsizes `3/6` away promises a fact it then hides.
     */
    @Test
    fun theNarrowestTerminalRungPrintsTheSuiteLineWithoutEllipsis() {
        val density = context.resources.displayMetrics.density
        val tier = WidgetTier.Terminal(2)
        val width = WidgetRenderer.breakpoints().getValue(tier).width
        val root = inflate(content(tier), tier)
        val widthPx = (width * density).toInt()
        val heightPx = (WidgetRenderer.minHeightDp(2) * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, widthPx, heightPx)

        // The date may fall off a narrow widget; `1/8` may not. That is the
        // whole reason the arithmetic leads the line.
        val line = root.findViewById<TextView>(R.id.widget_line1)
        val kept = line.text.length - line.layout.getEllipsisCount(0)
        assertTrue(
            "the narrowest terminal rung ellipsizes the suite arithmetic itself",
            kept >= "1/8 ▓▓░░".length
        )
    }

    /** True when every bound line is fully inside a widget of [heightPx]. */
    private fun fitsAt(tier: WidgetTier, widthPx: Int, heightPx: Int, lines: Int): Boolean {
        val root = inflate(content(tier), tier)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, widthPx, heightPx)
        return (1..lines).all { slot ->
            val id = context.resources.getIdentifier(
                "widget_line$slot", "id", context.packageName
            )
            val view = root.findViewById<View>(id) ?: return@all true
            if (view.visibility != View.VISIBLE) return@all true
            val bottom = IntArray(2).also { view.getLocationInWindow(it) }[1] + view.height
            view.height > 0 && bottom <= heightPx
        }
    }

    // ---- the opacity setting ---------------------------------------------

    @Test
    fun theFillLayerCarriesTheOpacityTheSettingAsksFor() {
        val pixel = layeredBackground(opacityPct = 50).first.centerPixel()
        // 50% of 255 = 127±: the alpha must actually land on the pixels.
        assertTrue("fill alpha ${Color.alpha(pixel)} not ~50%", Color.alpha(pixel) in 120..135)
        // ...and still be the Obsidian background underneath.
        assertNear(0x10, Color.red(pixel))
        assertNear(0x14, Color.green(pixel))
        assertNear(0x1A, Color.blue(pixel))
    }

    @Test
    fun theBorderLayerPaintsNothingButItsFrame() {
        val border = layeredBackground(opacityPct = 50).second
        // A filled border layer would sit opaque on top of the fill and make the
        // opacity setting look broken (the siblings' GradientDrawable bug, kept
        // fixed by the transparent <solid> in widget_bg_border.xml).
        assertEquals(
            "the frame layer must stay hollow, or it hides the fill underneath",
            0,
            Color.alpha(border.centerPixel())
        )
    }

    private fun Bitmap.centerPixel(): Int = getPixel(width / 2, height / 2)

    private fun assertNear(expected: Int, actual: Int) =
        assertTrue("expected ~$expected, was $actual", kotlin.math.abs(expected - actual) <= 2)

    /** Both background layers of a laid-out MEDIUM widget, each drawn alone. */
    private fun layeredBackground(opacityPct: Int): Pair<Bitmap, Bitmap> {
        val tier = WidgetTier.Terminal(4)
        val root = inflate(content(tier), tier, opacityPct)
        val size = (230 * context.resources.displayMetrics.density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, size, size)
        return draw(root.findViewById(R.id.widget_bg_fill), size) to
            draw(root.findViewById(R.id.widget_bg_border), size)
    }

    private fun draw(view: View, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
}
