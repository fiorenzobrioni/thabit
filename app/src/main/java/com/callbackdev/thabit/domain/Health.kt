package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Habit
import java.time.LocalDate
import kotlin.math.pow

/**
 * Test health — the forgiving half of the pair, and the app's primary strength
 * signal (VISION §3.3.3, adopted from Loop — §6.1).
 *
 * An exponential moving average over the test's **graded units** (due days, or
 * ISO weeks for a quota): a pass pulls it toward 1, a fail pulls it toward 0,
 * and skips and `no run` days are simply not in the sequence, so they move
 * nothing. One missed day dents it; it does not zero it.
 *
 * ### The numbers, stated once and never hidden
 *
 * - **Half-life: [HALF_LIFE_UNITS] graded units.** After fourteen due runs, an
 *   old result carries half the weight it did. `alpha = 1 - 2^(-1/14)`, about
 *   0.0483, so a single failure at 100% lands at roughly 95% — a dent.
 * - **Counted in graded units, not calendar days.** A `mon,wed,fri` test decays
 *   per *run*, exactly like a daily one: each due run weighs the same wherever
 *   it falls, and a test is not punished for being scheduled sparsely.
 * - **Seeded with the first graded outcome**, not with zero. Starting at zero
 *   would show a test passed three times out of three at 13% — a false number
 *   for a new test, which is the same lie the `createdAt` clamp exists to
 *   prevent (VISION §4.3).
 * - **Unknown, not zero, with no data.** A test with nothing graded returns
 *   null; the screens say nothing rather than showing a fabricated 0%.
 *
 * These four lines are the whole formula, and they are repeated verbatim in the
 * export header (Fase 11) because VISION §7 forbids secret numbers: every stat
 * the app shows must be recomputable by the user from their own file.
 */
object Health {

    /** Half-life in graded units — the one constant of the formula. */
    const val HALF_LIFE_UNITS: Int = 14

    /** `1 - 2^(-1/14)` ≈ 0.0483 — the EMA's smoothing factor. */
    val ALPHA: Double = 1.0 - 2.0.pow(-1.0 / HALF_LIFE_UNITS)

    /** The one-line statement of the formula, shared by the export and the docs. */
    const val FORMULA: String =
        "ema over graded units, half-life $HALF_LIFE_UNITS units, seeded with the first outcome, " +
            "skips and no-run days excluded"

    /** Health in 0..1, or null when the test has nothing graded yet. */
    fun of(history: SuiteHistory, habit: Habit, today: LocalDate): Double? =
        fromUnits(Outcomes.graded(history, habit, habit.createdAt, today, today))

    /** The same EMA over an already-built sequence — the form the tests read. */
    fun fromUnits(units: List<GradedUnit>): Double? {
        if (units.isEmpty()) return null
        var health = if (units.first().passed) 1.0 else 0.0
        for (unit in units.drop(1)) {
            val value = if (unit.passed) 1.0 else 0.0
            health += ALPHA * (value - health)
        }
        return health.coerceIn(0.0, 1.0)
    }

    /** `82` — the integer percentage the expansion and `stats.md` show. */
    fun percent(health: Double?): Int? = health?.let { Math.round(it * 100).toInt() }
}
