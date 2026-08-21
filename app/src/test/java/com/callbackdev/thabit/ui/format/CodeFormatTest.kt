package com.callbackdev.thabit.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

/**
 * The code channel's formatting: [Locale.ROOT], 24-hour, no scientific notation
 * and no trailing zeros. A comment is source, and source reads the same on every
 * phone.
 */
class CodeFormatTest {

    @Test
    fun `whole numbers lose their decimal point`() {
        assertEquals("23", CodeFormat.number(23.0))
        assertEquals("0", CodeFormat.number(0.0))
        assertEquals("2000", CodeFormat.number(2000.0))
    }

    @Test
    fun `fractions keep only what they need`() {
        assertEquals("2.5", CodeFormat.number(2.5))
        assertEquals("0.25", CodeFormat.number(0.25))
        assertEquals("1.5", CodeFormat.number(1.499999))
    }

    @Test
    fun `the decimal separator does not follow the phone's language`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY) // a locale that writes 2,5
            assertEquals("2.5", CodeFormat.number(2.5))
            assertEquals("07:12", CodeFormat.time(LocalTime.of(7, 12)))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `a counter always shows its arithmetic`() {
        assertEquals("12/30", CodeFormat.fraction(12.0, 30.0))
    }

    @Test
    fun `times are 24 hour and zero padded`() {
        assertEquals("07:12", CodeFormat.time(LocalTime.of(7, 12)))
        assertEquals("23:05", CodeFormat.time(LocalTime.of(23, 5)))
        assertEquals("00:00", CodeFormat.time(LocalTime.MIDNIGHT))
    }

    @Test
    fun `an unknown percentage says so instead of showing a zero`() {
        assertEquals("--%", CodeFormat.percent(null))
        assertEquals("82%", CodeFormat.percent(0.82))
        assertEquals("100%", CodeFormat.percent(1.0))
    }

    @Test
    fun `the health bar is ten cells, and an unknown health draws none of them`() {
        assertEquals("▓▓▓▓▓▓▓▓░░", CodeFormat.bar(0.82))
        assertEquals("░░░░░░░░░░", CodeFormat.bar(null))
        assertEquals("▓▓▓▓▓▓▓▓▓▓", CodeFormat.bar(1.0))
        assertEquals(CodeFormat.BAR_CELLS, CodeFormat.bar(0.37).length)
    }
}
