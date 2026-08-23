package com.callbackdev.thabit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A static guard on the widget layouts, and it exists because of a real bug.
 *
 * The launcher inflates widget layouts — **and `previewLayout`** — in a
 * restricted context that only knows a fixed set of view types. A plain `<View>`
 * is not one of them, so a single divider written that way makes the whole
 * inflation fail: the picker showed thabit's preview nearly blank, and nothing
 * in the build said a word about it. Lint does not check this, and neither does
 * a Robolectric inflate, because an ordinary `LayoutInflater` is perfectly happy
 * with a `<View>`.
 *
 * So the rule is asserted here instead, by reading the XML: every element in
 * every widget layout must be a type RemoteViews can inflate.
 */
class WidgetLayoutsTest {

    /**
     * The types `RemoteViews` will inflate (the `@RemoteView`-annotated widgets),
     * narrowed to the ones this project has any reason to use. `View` and `Space`
     * are deliberately absent — that absence is the whole point of this test.
     */
    private val allowed = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "TextView", "ImageView", "ImageButton", "Button", "ProgressBar",
        "Chronometer", "AnalogClock", "ViewFlipper", "ViewStub"
    )

    private val layouts: List<File> = File("src/main/res/layout")
        .listFiles { file -> file.name.startsWith("widget_") && file.extension == "xml" }
        ?.sortedBy { it.name }
        .orEmpty()

    private fun elements(file: File): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val found = mutableListOf<String>()
        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                found += (node as Element).tagName
                val children = node.childNodes
                for (index in 0 until children.length) walk(children.item(index))
            }
        }
        walk(document.documentElement)
        return found
    }

    @Test
    fun `the layouts are where this test thinks they are`() {
        // A test that silently found no files would pass forever.
        assertEquals(
            listOf(
                "widget_thabit_large.xml",
                "widget_thabit_medium.xml",
                "widget_thabit_preview.xml",
                "widget_thabit_small.xml"
            ),
            layouts.map { it.name }
        )
    }

    @Test
    fun `every widget layout uses only view types RemoteViews can inflate`() {
        layouts.forEach { file ->
            val rejected = elements(file).distinct().filterNot { it in allowed }
            assertTrue(
                "${file.name} uses $rejected, which the launcher's restricted " +
                    "inflater does not know: the widget comes up blank",
                rejected.isEmpty()
            )
        }
    }

    /**
     * The picker's preview is a static drawing of the medium tier, so it has to
     * *look* like it: same title bar, same prompt, same first field.
     */
    @Test
    fun `the preview draws the same widget the launcher will get`() {
        val preview = File("src/main/res/layout/widget_thabit_preview.xml").readText()
        assertTrue("the preview must show the prompt", preview.contains("you@thabit"))
        assertTrue("the prompt is the series' green", preview.contains("#74DD7E"))
        assertTrue("the suite field leads with its name", preview.contains("\"Suite\""))
        assertTrue("the preview must show the date", preview.contains("2026-08-21"))
    }
}
