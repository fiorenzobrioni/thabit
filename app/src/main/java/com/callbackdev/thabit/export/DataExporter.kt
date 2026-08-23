package com.callbackdev.thabit.export

import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** What one `$ thabit export` did, as the terminal will report it. */
sealed interface ExportResult {

    /** [files] are the names the store actually wrote. */
    data class Written(
        val files: List<String>,
        val tests: Int,
        val checks: Int,
        val days: Int
    ) : ExportResult

    /** Nothing recorded yet — an empty file is a worse answer than saying so. */
    data object Empty : ExportResult

    data class Failed(val message: String) : ExportResult
}

/**
 * The whole history handed back to the person who lived it.
 *
 * Reads through the repository, renders through [ExportDocuments], writes
 * through an [ExportSink] — one pass, no state, nothing scheduled: an export
 * happens because a command was tapped, and nothing about the database changes
 * because of it.
 *
 * What goes in is **everything**, and that is the whole design:
 *
 * - every test, **archived ones included**, with the day they left the suite.
 *   `[rm]` takes a test off today's file; it never takes back the history that
 *   test earned, and an archive that quietly dropped them would be the app
 *   deciding which of the user's past is worth keeping.
 * - every check row exactly as it was written — a skip window is one row with
 *   its `until`, not fourteen invented ones.
 * - **every presence row**, which is the unglamorous one that matters:
 *   coverage and `no run` are computed from the days the app was actually
 *   opened, so without them the user could not recompute the two statistics
 *   that say what the app does *not* know (VISION §3.3.8, §5).
 *
 * Nothing is derived on the way out. Streaks, health, build results and records
 * are all recomputable from these three tables plus the rules stated in the
 * header, and shipping the app's own answers instead would be shipping a
 * snapshot of a formula rather than the facts it runs on.
 */
class DataExporter(
    private val repository: HabitRepository,
    private val settings: SettingsStore,
    private val sink: ExportSink,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    suspend fun export(
        format: ExportFormat,
        nowMillis: Long = System.currentTimeMillis()
    ): ExportResult = try {
        val bundle = bundle(nowMillis)
        if (bundle.isEmpty) {
            ExportResult.Empty
        } else {
            val written = ExportDocuments.files(bundle, format).map { sink.write(it) }
            ExportResult.Written(
                files = written,
                tests = bundle.habits.size,
                checks = bundle.checks.size,
                days = bundle.days.size
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        // The storage layer's own words read like a compiler message already.
        ExportResult.Failed(error.message ?: error.javaClass.simpleName)
    }

    private suspend fun bundle(nowMillis: Long): ExportBundle {
        val config = settings.settings.first()
        val history = repository.fullHistory()
        return ExportBundle(
            exportedAtMillis = nowMillis,
            zone = zone(),
            // The logical day, not the wall one: at one in the morning with
            // `day_ends: 03:00` the export belongs to the day still running, and
            // its filename says so.
            logicalDate = repository.today(),
            dayEnds = config.dayEnds,
            weekStartsOn = config.weekStartsOn,
            habits = history.habits.sortedWith(compareBy({ it.position }, { it.id })),
            checks = repository.allChecks().sortedWith(compareBy({ it.date }, { it.habitId })),
            days = repository.presence().sortedBy { it.date }
        )
    }
}
