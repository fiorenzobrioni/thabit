package com.callbackdev.thabit.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What is still writable — the one honest exception to an immutable history
 * (VISION §3.3.5).
 *
 * Today is the working tree. Yesterday — the previous **logical** day — stays
 * amendable, because "I did it and forgot to tick it" is the habit tracker's
 * most common lie and the cure is a grace window, not a history anyone can
 * rewrite. Two days back is history, forever.
 *
 * The window is a definition, not a duration: it holds until the current logical
 * day ends at `day_ends`, so the grace shrinks from a full day to zero as today
 * passes. Coming back after a week away, the only amendable day is the most
 * recent closed one — the days before it are `no run` and stay that way. They
 * are not days to correct: they are days that did not happen.
 */
class AmendWindow(private val boundary: DayBoundary = DayBoundary.Default) {

    /** Can a check still be written for this logical date? */
    fun isWritable(date: LocalDate, now: Instant, zone: ZoneId): Boolean {
        val today = boundary.logicalDate(now, zone)
        return date == today || date == today.minusDays(1)
    }

    /** True for the amendable day itself — the log's `--amend` row. */
    fun isAmendable(date: LocalDate, now: Instant, zone: ZoneId): Boolean =
        date == boundary.logicalDate(now, zone).minusDays(1)

    /**
     * The instant the grace runs out — the `# still editable until 03:00` the
     * commit carries so the window is *declared* rather than discovered.
     *
     * A tappable row is invisible: nobody touches a line hoping it will open, so
     * without this the best mechanism in the app would go unused (VISION §4.2).
     */
    fun closesAt(now: Instant, zone: ZoneId): Instant =
        boundary.endOf(boundary.logicalDate(now, zone), zone)

    /** How much grace is left. Never negative. */
    fun remaining(now: Instant, zone: ZoneId): Duration =
        Duration.between(now, closesAt(now, zone)).let {
            if (it.isNegative) Duration.ZERO else it
        }
}
