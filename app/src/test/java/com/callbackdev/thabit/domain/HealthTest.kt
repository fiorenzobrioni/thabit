package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The health EMA — the forgiving half, with its constants nailed down.
 *
 * These tests are also the guarantee behind VISION §7's "no secret numbers":
 * the half-life is asserted here, stated in [Health.FORMULA] and repeated in the
 * export, so the user can recompute every percentage the app shows.
 */
class HealthTest {

    private val d = Fixture.D0
    private val habit = Fixture.habit()

    private fun units(vararg passed: Boolean) =
        passed.mapIndexed { i, ok -> GradedUnit(d.plusDays(i.toLong()), ok) }

    @Test
    fun `no data is unknown, never zero`() {
        assertNull(Health.fromUnits(emptyList()))
        assertNull(Health.of(Fixture.history(listOf(habit)), habit, d))
        assertNull(Health.percent(null))
    }

    @Test
    fun `a test passed three times out of three is at full health, not at thirteen percent`() {
        // Seeding the average with the first outcome instead of with zero is what
        // keeps a new test from being reported as a failing one.
        assertEquals(100, Health.percent(Health.fromUnits(units(true, true, true))))
    }

    @Test
    fun `one missed day dents health, it does not zero it`() {
        val health = Health.fromUnits(units(true, true, true, true, false))!!
        assertTrue("expected a dent, got $health", health in 0.93..0.97)
    }

    @Test
    fun `the half-life is exactly fourteen graded units`() {
        // From a perfect record, fourteen consecutive failures must land halfway
        // to zero: that is what a half-life of fourteen means, stated out loud.
        val record = List(30) { true } + List(Health.HALF_LIFE_UNITS) { false }
        val health = Health.fromUnits(units(*record.toBooleanArray()))!!
        assertTrue("expected ~0.5, got $health", abs(health - 0.5) < 0.005)
    }

    @Test
    fun `alpha is the documented function of the half-life`() {
        assertEquals(1.0 - Math.pow(2.0, -1.0 / 14.0), Health.ALPHA, 1e-12)
        assertTrue(Health.FORMULA.contains("half-life ${Health.HALF_LIFE_UNITS}"))
    }

    @Test
    fun `skips are neutral - they never enter the average`() {
        val today = d.plusDays(4)
        val withSkip = Fixture.history(
            listOf(habit),
            listOf(
                Fixture.pass(habit.id, d),
                Fixture.skip(habit.id, d.plusDays(1)),
                Fixture.pass(habit.id, d.plusDays(2)),
                Fixture.skip(habit.id, d.plusDays(3)),
                Fixture.pass(habit.id, today)
            ),
            Fixture.ranFrom(d, today)
        )
        val withoutSkip = Fixture.greenRun(habit, d, d.plusDays(2))
        assertEquals(
            Health.of(withoutSkip, habit, d.plusDays(2)),
            Health.of(withSkip, habit, today)
        )
    }

    @Test
    fun `a no-run day is neutral for health, even though it breaks the streak`() {
        // The asymmetry, asserted from both sides in one test.
        val today = d.plusDays(4)
        val present = Fixture.ranFrom(d, today) - d.plusDays(2)
        val checks = listOf(0L, 1L, 3L, 4L).map { Fixture.pass(habit.id, d.plusDays(it)) }
        val history = Fixture.history(listOf(habit), checks, present)

        assertEquals(100, Health.percent(Health.of(history, habit, today)))
        assertEquals(2, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `health recovers when the passes come back`() {
        val broken = Health.fromUnits(units(*(List(20) { true } + List(10) { false }).toBooleanArray()))!!
        val recovered = Health.fromUnits(
            units(*(List(20) { true } + List(10) { false } + List(10) { true }).toBooleanArray())
        )!!
        assertTrue(recovered > broken)
    }

    @Test
    fun `health stays inside zero and one`() {
        val all = Health.fromUnits(units(*BooleanArray(40) { true }))!!
        val none = Health.fromUnits(units(*BooleanArray(40) { false }))!!
        assertTrue(all <= 1.0 && all > 0.99)
        assertTrue(none >= 0.0 && none < 0.01)
    }
}
