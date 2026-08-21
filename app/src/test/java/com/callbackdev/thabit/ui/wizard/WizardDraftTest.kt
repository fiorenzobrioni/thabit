package com.callbackdev.thabit.ui.wizard

import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The conversation as a value: what it defaults to, where each answer moves it,
 * and what comes out at the end.
 */
class WizardDraftTest {

    private val today = LocalDate.of(2026, 8, 21)

    // ---- the promise of one answer ---------------------------------------

    @Test
    fun `everything except the name has a default`() {
        val draft = WizardDraft()
        assertEquals("", draft.name)
        assertEquals(HabitType.BOOLEAN, draft.type)
        assertEquals(Schedule.Daily, draft.schedule)
        assertNull(draft.emoji)
        assertFalse(draft.expanded)
    }

    @Test
    fun `a name is the only thing a test cannot be added without`() {
        assertEquals("ERROR: a test needs a name", WizardDraft().validationError())
        assertTrue(WizardDraft().withName("meditate 10 min").isValid)
    }

    @Test
    fun `one answer produces a whole test`() {
        val habit = WizardDraft().withName("  meditate 10 min  ").toHabit(today, position = 3)
        assertEquals("meditate 10 min", habit.name) // trimmed: the file is not padded
        assertEquals(HabitType.BOOLEAN, habit.type)
        assertEquals(Schedule.Daily, habit.schedule)
        assertNull(habit.assert)
        assertEquals(today, habit.createdAt)
        assertEquals(3, habit.position)
    }

    // ---- the counter ------------------------------------------------------

    @Test
    fun `a counter carries its assertion, written the way the file writes it`() {
        val draft = WizardDraft()
            .withName("read")
            .withType(HabitType.COUNTER)
            .withUnit("pages")
            .withTarget("20")
        assertEquals("pages >= 20", draft.assertText)
        val habit = draft.toHabit(today, 0)
        assertEquals(AssertSpec(20.0, "pages", 1.0), habit.assert)
    }

    @Test
    fun `the step is always one, and the wizard never invents a set size`() {
        // `[+1]` then appears exactly when the target is within a dozen taps.
        val habit = WizardDraft()
            .withName("water")
            .withType(HabitType.COUNTER)
            .withUnit("glasses")
            .withTarget("3")
            .toHabit(today, 0)
        assertEquals(1.0, habit.assert!!.step, 0.0)
    }

    @Test
    fun `an unreadable target leaves the one that was there`() {
        val draft = WizardDraft().withType(HabitType.COUNTER).withTarget("20")
        assertEquals(20.0, draft.withTarget("banana").target, 0.0)
        assertEquals(20.0, draft.withTarget("").target, 0.0)
        assertEquals(20.0, draft.withTarget("-5").target, 0.0)
        assertEquals(20.0, draft.withTarget("0").target, 0.0)
    }

    @Test
    fun `a decimal comma is read the way the user typed it`() {
        assertEquals(1.5, WizardDraft().withTarget("1,5").target, 0.0)
    }

    @Test
    fun `only a counter gets an assertion`() {
        listOf(HabitType.BOOLEAN, HabitType.AVOID).forEach { type ->
            val habit = WizardDraft().withName("x").withType(type).toHabit(today, 0)
            assertNull("$type should not assert a number", habit.assert)
        }
    }

    // ---- the schedule -----------------------------------------------------

    @Test
    fun `each scheme spells out its own token`() {
        val draft = WizardDraft()
        assertEquals("daily", draft.scheduleToken(ScheduleScheme.Daily))
        assertEquals("mon,tue,wed,thu,fri", draft.scheduleToken(ScheduleScheme.Weekdays))
        assertEquals("3/week", draft.scheduleToken(ScheduleScheme.Quota))
        assertEquals("every 2d", draft.scheduleToken(ScheduleScheme.Interval))
    }

    @Test
    fun `choosing a scheme picks up its parameters`() {
        val quota = WizardDraft().withScheme(ScheduleScheme.Quota).cycleQuota()
        assertEquals(Schedule.Quota(4), quota.schedule)

        val interval = WizardDraft().withScheme(ScheduleScheme.Interval).cycleInterval()
        assertEquals(Schedule.Interval(3), interval.schedule)
    }

    @Test
    fun `the quota cycles one to seven and wraps`() {
        var draft = WizardDraft().withScheme(ScheduleScheme.Quota)
        repeat(WizardDraft.MAX_QUOTA - WizardDraft.DEFAULT_QUOTA) { draft = draft.cycleQuota() }
        assertEquals(7, draft.quota)
        assertEquals(1, draft.cycleQuota().quota)
    }

    @Test
    fun `the interval walks its stops and wraps`() {
        assertEquals(
            WizardDraft.INTERVAL_CYCLE.drop(1) + WizardDraft.INTERVAL_CYCLE.first(),
            generateSequence(WizardDraft(intervalDays = WizardDraft.INTERVAL_CYCLE.first())) {
                it.cycleInterval()
            }.drop(1).take(WizardDraft.INTERVAL_CYCLE.size).map { it.intervalDays }.toList()
        )
    }

    @Test
    fun `an interval outside the stops moves forward instead of snapping back`() {
        assertEquals(7, WizardDraft(intervalDays = 6).cycleInterval().intervalDays)
        assertEquals(2, WizardDraft(intervalDays = 99).cycleInterval().intervalDays)
    }

    @Test
    fun `a weekday can be added and removed`() {
        val draft = WizardDraft().withScheme(ScheduleScheme.Weekdays)
        val withSaturday = draft.toggleWeekday(DayOfWeek.SATURDAY)
        assertTrue(DayOfWeek.SATURDAY in withSaturday.weekdays)
        assertFalse(DayOfWeek.SATURDAY in withSaturday.toggleWeekday(DayOfWeek.SATURDAY).weekdays)
    }

    @Test
    fun `the last weekday cannot be removed`() {
        // A weekday schedule with no days is a test that is never due — a quiet
        // way of archiving something the user only meant to un-tick.
        var draft = WizardDraft(weekdays = setOf(DayOfWeek.MONDAY))
        draft = draft.toggleWeekday(DayOfWeek.MONDAY)
        assertEquals(setOf(DayOfWeek.MONDAY), draft.weekdays)
    }

    // ---- the emoji --------------------------------------------------------

    @Test
    fun `the emoji is optional and can be taken back`() {
        val draft = WizardDraft().withName("read").withEmoji("📖")
        assertEquals("📖", draft.toHabit(today, 0).emoji)
        assertNull(draft.withEmoji(null).emoji)
        assertNull(draft.withEmoji("   ").emoji)
    }

    // ---- reopening an existing test ---------------------------------------

    @Test
    fun `an edit opens prefilled and already unfolded`() {
        val habit = Habit(
            id = 7L,
            name = "read 20 pages",
            type = HabitType.COUNTER,
            assert = AssertSpec(20.0, "pages", 1.0),
            schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            emoji = "📖",
            position = 4,
            createdAt = today.minusDays(30)
        )
        val draft = WizardDraft.of(habit)
        assertEquals(7L, draft.editing)
        assertTrue(draft.isEditing)
        assertTrue(draft.expanded) // the reader came to change one specific thing
        assertEquals("read 20 pages", draft.name)
        assertEquals(ScheduleScheme.Weekdays, draft.scheme)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), draft.weekdays)
        assertEquals("pages", draft.unit)
        assertEquals(20.0, draft.target, 0.0)
        assertEquals("📖", draft.emoji)
    }

    @Test
    fun `an edit never moves a test's own history`() {
        val habit = Habit(
            id = 7L,
            name = "read",
            position = 4,
            createdAt = today.minusDays(30),
            archivedAt = null
        )
        val edited = WizardDraft.of(habit).withName("read 20 pages").applyTo(habit)
        assertEquals("read 20 pages", edited.name)
        assertEquals(today.minusDays(30), edited.createdAt) // not younger for being renamed
        assertEquals(4, edited.position)
        assertEquals(7L, edited.id)
    }

    @Test
    fun `changing type away from counter drops the assertion`() {
        val habit = Habit(
            id = 7L,
            name = "read",
            type = HabitType.COUNTER,
            assert = AssertSpec(20.0, "pages", 1.0),
            createdAt = today
        )
        val edited = WizardDraft.of(habit).withType(HabitType.BOOLEAN).applyTo(habit)
        assertNull(edited.assert)
        assertNotNull(WizardDraft.of(habit).applyTo(habit).assert)
    }

    @Test
    fun `a counter needs something to count`() {
        val draft = WizardDraft().withName("read").withType(HabitType.COUNTER).withUnit("")
        assertEquals("ERROR: a counter needs a unit to count", draft.validationError())
    }
}
