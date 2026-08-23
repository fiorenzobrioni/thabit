package com.callbackdev.thabit.export

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The exporter against a real database.
 *
 * The assertions that matter are about **what is in the archive**, because the
 * promise of the feature is that the user can recompute every statistic from it:
 * the archived tests, the presence rows, and the check rows exactly as written.
 * The JSON is parsed rather than pattern-matched, because a document that only
 * looks like JSON is not an archive.
 */
@RunWith(RobolectricTestRunner::class)
class DataExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** 21 August 2026, 01:00 in Rome. With `day_ends: 03:00` the logical day is the 20th. */
    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-08-20T23:00:00Z"), ZoneId.of("Europe/Rome"))

    private lateinit var db: ThabitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repository: HabitRepository
    private lateinit var sink: RecordingSink
    private lateinit var exporter: DataExporter

    @Before
    fun setUp() {
        db = ThabitDatabase.inMemory(ApplicationProvider.getApplicationContext())
        settings = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") },
            clock
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings, clock)
        sink = RecordingSink()
        exporter = DataExporter(repository, settings, sink) { ZoneId.of("Europe/Rome") }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun exportJson(): JsonObject {
        val result = exporter.export(ExportFormat.JSON, clock.millis())
        assertTrue("export failed: $result", result is ExportResult.Written)
        return Json.parseToJsonElement(sink.written.single().content).jsonObject
    }

    private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

    private fun JsonObject.text(key: String): String? =
        get(key)?.jsonPrimitive?.takeIf { !it.toString().equals("null", true) }?.content

    @Test
    fun `an empty database says so instead of writing an empty file`() = runTest {
        assertEquals(ExportResult.Empty, exporter.export(ExportFormat.JSON, clock.millis()))
        assertTrue(sink.written.isEmpty())
    }

    @Test
    fun `the document is valid JSON, not something that merely looks like it`() = runTest {
        repository.addHabit("""read "Dune", 20 pages""", HabitType.COUNTER, AssertSpec(20.0, "pages"))
        val document = exportJson()
        assertEquals("thabit", document.text("app"))
        assertEquals(ExportDocuments.SCHEMA_VERSION, document.getValue("schema").jsonPrimitive.content.toInt())
        // The awkward name survived the escape and came back identical.
        assertEquals(
            """read "Dune", 20 pages""",
            document.array("tests").single().jsonObject.text("name")
        )
    }

    @Test
    fun `an archived test is in the archive — the history is the user's`() = runTest {
        val id = repository.addHabit("gym")
        repository.archiveHabit(id)

        val tests = exportJson().array("tests").map { it.jsonObject }
        assertEquals(1, tests.size)
        assertNotNull("an archived test must keep the day it left", tests.single().text("archived_at"))
    }

    @Test
    fun `every presence row ships, because coverage is computed from them`() = runTest {
        repository.addHabit("meditate 10 min")
        // The app was opened: the day ran.
        repository.markPresent()

        // Default `day_ends` is midnight, so at 01:00 the logical day is the 21st.
        val days = exportJson().array("days").map { it.jsonObject }
        assertEquals(listOf("2026-08-21"), days.map { it.text("date") })
        // Without this table, `no run` and coverage would be unverifiable.
        assertNotNull(days.single().text("first_seen"))
    }

    @Test
    fun `the export belongs to the logical day, not to the wall one`() = runTest {
        settings.setDayEnds(LocalTime.of(3, 0))
        repository.addHabit("meditate 10 min")

        // The wall clock says the 21st; `day_ends: 03:00` says the day is the 20th.
        val document = exportJson()
        assertEquals("2026-08-20", document.text("logical_date"))
        assertEquals("thabit-export-2026-08-20.json", sink.written.single().name)
    }

    @Test
    fun `a skip window is one row, and the rule to expand it is in the header`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        repository.skip(id, repository.today(), note = "away", until = repository.today().plusDays(6))

        val document = exportJson()
        val checks = document.array("checks").map { it.jsonObject }
        assertEquals(1, checks.size)
        assertEquals("2026-08-27", checks.single().text("until"))
        // One row, and the arithmetic to recover the other six days.
        assertEquals(
            ExportDocuments.SKIP_WINDOW_RULE,
            document.getValue("rules").jsonObject.text("skip_window")
        )
    }

    @Test
    fun `CSV writes three tables and the terminal is told all three names`() = runTest {
        repository.addHabit("meditate 10 min")
        val result = exporter.export(ExportFormat.CSV, clock.millis()) as ExportResult.Written
        assertEquals(3, sink.written.size)
        // The names reported are the store's answers, not the exporter's requests.
        assertTrue(result.files.all { it.endsWith(" (renamed)") })
        assertEquals(1, result.tests)
    }

    @Test
    fun `a sink that throws becomes a message, never a crash`() = runTest {
        repository.addHabit("meditate 10 min")
        sink.failWith = "Downloads is not writable"
        assertEquals(
            ExportResult.Failed("Downloads is not writable"),
            exporter.export(ExportFormat.JSON, clock.millis())
        )
    }

    @Test
    fun `exporting writes nothing to the database`() = runTest {
        val id = repository.addHabit("meditate 10 min")
        repository.pass(id, LocalDate.of(2026, 8, 20))
        repository.markPresent()
        val checksBefore = db.checkDao().all()
        val daysBefore = db.dayDao().all()

        exporter.export(ExportFormat.JSON, clock.millis())

        // Handing the history back is not an interaction with it.
        assertEquals(checksBefore, db.checkDao().all())
        assertEquals(daysBefore, db.dayDao().all())
    }

    private class RecordingSink : ExportSink {
        val written = mutableListOf<ExportFile>()
        var failWith: String? = null

        override suspend fun write(file: ExportFile): String {
            failWith?.let { throw IOException(it) }
            written += file
            return "${file.name} (renamed)"
        }
    }
}
