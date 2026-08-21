package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * `HEAD → 9e31c7a`: seven hex characters, stable and derived.
 */
class CommitHashTest {

    @Test
    fun `a hash is seven hex characters`() {
        val hash = CommitHash.of(LocalDate.of(2026, 8, 21))
        assertEquals(7, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{7}")))
    }

    @Test
    fun `the same day always hashes the same, before and after an amend`() {
        val date = LocalDate.of(2026, 8, 21)
        assertEquals(CommitHash.of(date), CommitHash.of(date))
    }

    @Test
    fun `different days hash differently`() {
        assertNotEquals(
            CommitHash.of(LocalDate.of(2026, 8, 21)),
            CommitHash.of(LocalDate.of(2026, 8, 22))
        )
    }

    @Test
    fun `the hash does not change with the default locale`() {
        val date = LocalDate.of(2026, 8, 21)
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val turkish = CommitHash.of(date)
            Locale.setDefault(Locale.ITALY)
            assertEquals(turkish, CommitHash.of(date))
        } finally {
            Locale.setDefault(default)
        }
    }
}
