package com.callbackdev.thabit.domain

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

/**
 * The seven hex characters a day's commit wears in `habits_history.diff`.
 *
 * SHA-1 of `thabit:<iso date>`, truncated the way git truncates — the series'
 * pattern, ported from tsteps. It is stable (the same day always hashes the
 * same, before and after an amend), derived (nothing is stored), and pure
 * decoration: `HEAD → 9e31c7a` is a signature, never the only place a fact
 * lives (VISION §3.3.7).
 *
 * [Locale.ROOT] on the formatting because a hash that changed in Turkish would
 * be a very funny bug to find in a file that must not lie.
 */
object CommitHash {

    private const val LENGTH = 7

    fun of(date: LocalDate): String = of("thabit:$date")

    fun of(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray())
        return buildString(LENGTH) {
            var i = 0
            while (length < LENGTH) {
                append(String.format(Locale.ROOT, "%02x", digest[i]))
                i++
            }
        }.take(LENGTH)
    }
}
