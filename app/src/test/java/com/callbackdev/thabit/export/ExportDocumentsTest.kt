package com.callbackdev.thabit.export

import com.callbackdev.thabit.domain.FlakyTests
import com.callbackdev.thabit.domain.Health
import com.callbackdev.thabit.domain.Regressions
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.DayPresence
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The archive, word by word.
 *
 * The rule under test throughout is VISION §5's: **every statistic must be
 * recomputable from this file**. That means the rows go out as they were
 * written, the presence table goes out at all, and every formula the app
 * computes with is stated in the header rather than kept in the app.
 */
class ExportDocumentsTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val exportedAt = Instant.parse("2026-08-21T07:00:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 8, 21)

    private val meditate = Habit(
        id = 1L,
        name = "meditate 10 min",
        remindAt = LocalTime.of(7, 0),
        position = 0,
        createdAt = LocalDate.of(2026, 8, 1)
    )
    private val read = Habit(
        id = 2L,
        name = "read 20 pages",
        type = HabitType.COUNTER,
        assert = AssertSpec(target = 20.0, unit = "pages", step = 1.0),
        schedule = Schedule.Quota(3),
        emoji = "📖",
        position = 1,
        createdAt = LocalDate.of(2026, 8, 1)
    )
    private val gym = Habit(
        id = 3L,
        name = "gym",
        schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        position = 2,
        createdAt = LocalDate.of(2026, 7, 1),
        archivedAt = LocalDate.of(2026, 8, 10)
    )

    private fun bundle(
        habits: List<Habit> = listOf(meditate, read, gym),
        checks: List<Check> = listOf(
            Check(1L, LocalDate.of(2026, 8, 20), CheckState.PASS, at = LocalTime.of(7, 12)),
            Check(2L, LocalDate.of(2026, 8, 20), CheckState.PROGRESS, value = 12.0),
            Check(
                habitId = 1L,
                date = LocalDate.of(2026, 8, 18),
                state = CheckState.SKIP,
                note = "away",
                at = LocalTime.of(8, 0),
                until = LocalDate.of(2026, 8, 19)
            )
        ),
        days: List<DayPresence> = listOf(
            DayPresence(LocalDate.of(2026, 8, 20), Instant.parse("2026-08-20T05:12:03Z").toEpochMilli()),
            DayPresence(LocalDate.of(2026, 8, 18), Instant.parse("2026-08-18T06:00:00Z").toEpochMilli(), amended = true)
        )
    ) = ExportBundle(
        exportedAtMillis = exportedAt,
        zone = rome,
        logicalDate = today,
        dayEnds = LocalTime.of(3, 0),
        weekStartsOn = DayOfWeek.MONDAY,
        habits = habits,
        checks = checks,
        days = days
    )

    // ---- the header --------------------------------------------------------

    @Test
    fun `the header states every rule the app computes with`() {
        val json = ExportDocuments.json(bundle())
        // Not paraphrases: the very constants the screens use, so the archive
        // cannot drift from the app that wrote it.
        assertTrue(json.contains(Health.FORMULA))
        assertTrue(json.contains(Regressions.RULE))
        assertTrue(json.contains(FlakyTests.RULE))
        assertTrue(json.contains(ExportDocuments.QUOTA_RULE))
        assertTrue(json.contains(ExportDocuments.SKIP_WINDOW_RULE))
        assertTrue(json.contains(ExportDocuments.NO_RUN_RULE))
    }

    @Test
    fun `the header states the settings a verdict depends on`() {
        val json = ExportDocuments.json(bundle())
        // `day_ends` decides which day a tap belongs to, so a reader who does not
        // know it cannot recompute a single verdict.
        assertTrue(json.contains(""""day_ends": "03:00""""))
        assertTrue(json.contains(""""week_starts": "monday""""))
        assertTrue(json.contains(""""timezone": "Europe/Rome""""))
        assertTrue(json.contains(""""exported_at": "2026-08-21T07:00:00Z""""))
        // The logical day, not the wall one.
        assertTrue(json.contains(""""logical_date": "2026-08-21""""))
    }

    // ---- the suite ---------------------------------------------------------

    @Test
    fun `a test travels whole, assertion included`() {
        val json = ExportDocuments.json(bundle())
        assertTrue(
            json.contains(
                """{ "id": 2, "name": "read 20 pages", "type": "counter", "when": "3/week", """ +
                    """"assert": { "unit": "pages", "target": 20, "step": 1 }, "remind": null, """ +
                    """"emoji": "📖", "position": 1, "created_at": "2026-08-01", """ +
                    """"archived_at": null }"""
            )
        )
    }

    @Test
    fun `an archived test is in the archive, with the day it left the suite`() {
        val json = ExportDocuments.json(bundle())
        // `[rm]` takes a test off today's file; it never takes back the history
        // that test earned.
        assertTrue(json.contains(""""name": "gym""""))
        assertTrue(json.contains(""""archived_at": "2026-08-10""""))
    }

    @Test
    fun `the schedule is spelled the way the file and the database spell it`() {
        val json = ExportDocuments.json(bundle())
        assertTrue(json.contains(""""when": "mon,thu""""))
        assertTrue(json.contains(""""when": "daily""""))
    }

    // ---- the checks --------------------------------------------------------

    @Test
    fun `a check row goes out as it was written`() {
        val json = ExportDocuments.json(bundle())
        assertTrue(
            json.contains(
                """{ "test_id": 1, "date": "2026-08-20", "state": "pass", "value": null, """ +
                    """"note": null, "at": "07:12", "until": null }"""
            )
        )
    }

    @Test
    fun `a skip window is one row with its until, never fourteen invented ones`() {
        val json = ExportDocuments.json(bundle())
        assertTrue(
            json.contains(
                """{ "test_id": 1, "date": "2026-08-18", "state": "skip", "value": null, """ +
                    """"note": "away", "at": "08:00", "until": "2026-08-19" }"""
            )
        )
        // The 19th has no row of its own: it is covered, not interacted with.
        assertFalse(json.contains(""""date": "2026-08-19""""))
    }

    @Test
    fun `a counter mid-way keeps its number and its own state`() {
        val json = ExportDocuments.json(bundle())
        // `progress` is a fact the user typed, not a verdict somebody derived.
        assertTrue(json.contains(""""state": "progress", "value": 12"""))
    }

    // ---- the days ----------------------------------------------------------

    @Test
    fun `the presence table ships, because coverage is computed from it`() {
        val json = ExportDocuments.json(bundle())
        assertTrue(
            json.contains(
                """{ "date": "2026-08-20", "first_seen": "2026-08-20T07:12:03+02:00", """ +
                    """"amended": false }"""
            )
        )
        assertTrue(json.contains(""""amended": true"""))
    }

    // ---- CSV ---------------------------------------------------------------

    @Test
    fun `CSV is three tables, because these are three kinds of row`() {
        val files = ExportDocuments.files(bundle(), ExportFormat.CSV)
        assertEquals(
            listOf(
                "thabit-suite-2026-08-21.csv",
                "thabit-checks-2026-08-21.csv",
                "thabit-days-2026-08-21.csv"
            ),
            files.map { it.name }
        )
        assertTrue(files.all { it.mimeType == ExportDocuments.CSV_MIME })
    }

    @Test
    fun `JSON is one document, because it can nest`() {
        val files = ExportDocuments.files(bundle(), ExportFormat.JSON)
        assertEquals("thabit-export-2026-08-21.json", files.single().name)
        assertEquals(ExportDocuments.JSON_MIME, files.single().mimeType)
    }

    @Test
    fun `the suite table names every column it carries`() {
        val csv = ExportDocuments.suiteCsv(bundle()).lines()
        assertEquals(
            "id,name,type,when,assert_unit,assert_target,assert_step," +
                "remind,emoji,position,created_at,archived_at",
            csv.first()
        )
        assertEquals("1,meditate 10 min,boolean,daily,,,,07:00,,0,2026-08-01,", csv[1])
        assertEquals("2,read 20 pages,counter,3/week,pages,20,1,,📖,1,2026-08-01,", csv[2])
    }

    @Test
    fun `the checks and days tables carry their own columns`() {
        assertEquals(
            "test_id,date,state,value,note,at,until",
            ExportDocuments.checksCsv(bundle()).lines().first()
        )
        assertEquals("date,first_seen,amended", ExportDocuments.daysCsv(bundle()).lines().first())
        assertTrue(
            ExportDocuments.daysCsv(bundle()).contains("2026-08-18T08:00:00+02:00,true")
        )
    }

    /**
     * Unlike the siblings' machine-made cells, these really can contain a comma:
     * a habit's name and a skip's note are the user's own words.
     */
    @Test
    fun `a name with a comma or a quote survives the round trip`() {
        val awkward = meditate.copy(name = """read "Dune", 20 pages""")
        val csv = ExportDocuments.suiteCsv(bundle(habits = listOf(awkward))).lines()[1]
        assertTrue(csv.contains(""""read ""Dune"", 20 pages""""))

        val json = ExportDocuments.json(bundle(habits = listOf(awkward)))
        assertTrue(json.contains("""\"Dune\", 20 pages"""))
    }

    @Test
    fun `a newline in a note does not break either format`() {
        val note = Check(
            1L, LocalDate.of(2026, 8, 20), CheckState.SKIP, note = "ill\nall week",
            at = LocalTime.of(9, 0)
        )
        val csv = ExportDocuments.checksCsv(bundle(checks = listOf(note)))
        // Quoted, so the record is still one record as far as a parser cares.
        assertTrue(csv.contains("\"ill\nall week\""))
        assertTrue(ExportDocuments.json(bundle(checks = listOf(note))).contains("""ill\nall week"""))
    }

    // ---- the empty archive -------------------------------------------------

    @Test
    fun `an empty archive is still a valid document, with empty arrays`() {
        val empty = bundle(habits = emptyList(), checks = emptyList(), days = emptyList())
        val json = ExportDocuments.json(empty)
        assertTrue(json.contains(""""tests": []"""))
        assertTrue(json.contains(""""checks": []"""))
        assertTrue(json.contains(""""days": []"""))
        assertTrue(empty.isEmpty)
    }

    @Test
    fun `a suite with no history yet is not an empty archive`() {
        // The tests themselves are worth exporting: they are what the user wrote.
        assertFalse(bundle(checks = emptyList(), days = emptyList()).isEmpty)
    }
}
