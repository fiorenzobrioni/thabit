package com.callbackdev.thabit.ui.format

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formatting for the **code channel**: the comments, values and clocks that live
 * inside the fake files.
 *
 * Everything here is [Locale.ROOT] and 24-hour on purpose. A comment is source,
 * not chrome (VISION §1.3), so `# 07:12` reads the same on every device — and a
 * decimal separator that changed with the phone's language would put two
 * different numbers in the file and the export for the same fact.
 *
 * This is the file tsteps' `UnitFormat` would have been if it had not been three
 * quarters pedometer domain (km/mi, pace, speed). Fase 1 deliberately left that
 * one behind; this is thabit writing the little it actually needs.
 */
object CodeFormat {

    /** `23`, `2.5`, `0.25` — trailing zeros dropped, never scientific notation. */
    fun number(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "?"
        val rounded = (value * 100).roundToInt() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    /** `12/30` — the arithmetic that always travels with a counter (VISION §3.3.7). */
    fun fraction(value: Double, target: Double): String = "${number(value)}/${number(target)}"

    /** `07:12`, always 24-hour: the file is code, not chrome. */
    fun time(time: LocalTime): String =
        String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)

    /** ISO-8601, the same spelling the database and the export use. */
    fun date(date: LocalDate): String = date.toString()

    /** `82%`, or `--%` when the app genuinely does not know yet. */
    fun percent(fraction: Double?): String =
        if (fraction == null) "--%" else "${(fraction * 100).roundToInt()}%"

    /**
     * `▓▓▓▓▓▓▓▓░░` — the health meter drawn in text, like everything else.
     *
     * An unknown health draws an empty bar rather than a full one: the app says
     * "I have nothing on this yet", it does not flatter.
     */
    fun bar(fraction: Double?, cells: Int = BAR_CELLS): String {
        val filled = ((fraction ?: 0.0).coerceIn(0.0, 1.0) * cells).roundToInt()
        return "▓".repeat(filled) + "░".repeat(cells - filled)
    }

    const val BAR_CELLS: Int = 10
}
