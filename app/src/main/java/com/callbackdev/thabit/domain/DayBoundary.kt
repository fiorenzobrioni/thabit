package com.callbackdev.thabit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The logical day — the app's only notion of "today".
 *
 * `day_ends` is the category's most requested setting (VISION §6.3): someone who
 * goes to bed at 02:00 is still living the previous day, and an app that rolls
 * over at midnight fails their last two hours every night. With `day_ends`
 * `03:00`, logical day D runs from D at 03:00 to D+1 at 03:00.
 *
 * The default is midnight, where logical date and wall date are the same and the
 * whole mechanism is invisible — which is why the main file only *states* the
 * logical day when it diverges from the wall date (VISION §4.1): at 01:00 the
 * phone says the 21st while the suite is still the 20th's, and an undeclared
 * divergence looks like a bug.
 *
 * Pure and injectable: nothing here reads the system clock. DST, timezone moves
 * and mid-day `day_ends` edits are test cases, not surprises.
 */
data class DayBoundary(val dayEnds: LocalTime = LocalTime.MIDNIGHT) {

    /** The logical date an instant belongs to. */
    fun logicalDate(instant: Instant, zone: ZoneId): LocalDate {
        val local = instant.atZone(zone)
        return if (local.toLocalTime() < dayEnds) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    /**
     * The instant logical day [date] begins.
     *
     * DST is resolved by [ZonedDateTime.of]'s own rules and that is the honest
     * answer: on a spring-forward night a `day_ends` of 02:30 simply does not
     * exist, so the boundary moves forward with the clock; on a fall-back night
     * the earlier of the two 02:30s wins, so the day is 25 hours long and the
     * user gets an hour of grace rather than an hour of missing day.
     */
    fun startOf(date: LocalDate, zone: ZoneId): Instant =
        ZonedDateTime.of(date, dayEnds, zone).toInstant()

    /** The instant logical day [date] ends — the same instant D+1 begins. */
    fun endOf(date: LocalDate, zone: ZoneId): Instant = startOf(date.plusDays(1), zone)

    /** True while [instant] still falls inside logical day [date]. */
    fun contains(date: LocalDate, instant: Instant, zone: ZoneId): Boolean =
        logicalDate(instant, zone) == date

    companion object {
        /** Midnight: logical date == wall date, the mechanism invisible. */
        val Default = DayBoundary(LocalTime.MIDNIGHT)
    }
}
