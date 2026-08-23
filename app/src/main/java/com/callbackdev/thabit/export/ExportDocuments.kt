package com.callbackdev.thabit.export

import com.callbackdev.thabit.domain.FlakyTests
import com.callbackdev.thabit.domain.Health
import com.callbackdev.thabit.domain.Regressions
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.DayPresence
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.ui.format.CodeFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The two shapes the archive can take, and the command that writes each. */
enum class ExportFormat(val command: String) {
    JSON("thabit export --json"),
    CSV("thabit export --csv")
}

/** Everything one export writes, gathered and ready to be rendered. */
data class ExportBundle(
    val exportedAtMillis: Long,
    val zone: ZoneId,
    /** The logical day the export was taken on — not necessarily the wall date. */
    val logicalDate: LocalDate,
    val dayEnds: LocalTime,
    val weekStartsOn: DayOfWeek,
    /** The whole suite, archived tests included: the history is the user's. */
    val habits: List<Habit>,
    /** Every check row **as stored** — a skip window is one row, with its `until`. */
    val checks: List<Check>,
    /** Every presence row: this is what makes `no run` recomputable. */
    val days: List<DayPresence>
) {
    val isEmpty: Boolean get() = habits.isEmpty() && checks.isEmpty() && days.isEmpty()
}

/** A rendered file, ready for the sink: name, MIME type, whole content. */
data class ExportFile(val name: String, val mimeType: String, val content: String)

/**
 * Renders the archive. Pure and unit-testable: no Android, no I/O — the sink
 * takes what comes out of here and puts it in Downloads.
 *
 * Three decisions the format hangs on.
 *
 * **Every rule that produces a number is in the header.** Health's half-life,
 * the regression rule, the flaky rule, how a quota is graded, what a `no run`
 * day does to a denominator. They are the same constants the app computes with
 * ([Health.FORMULA], [Regressions.RULE], [FlakyTests.RULE] — written in Fase 2
 * precisely so there would be one copy), so the export cannot drift from the
 * screens. A statistic the user cannot recompute from their own file is a secret
 * formula, and this app does not have any (VISION §5, §7).
 *
 * **Rows are exported as they were written, never as they are read.** A
 * `[~ skip] until:` is one row covering fourteen days, and that is what comes
 * out: fourteen invented rows would claim fourteen interactions the user never
 * made. The expansion rule is stated in the header instead, so the count is
 * still recomputable — the honest trade is "state the rule", not "materialise
 * the consequence".
 *
 * **The rules ride the JSON, not the CSV.** A CSV has no comment channel a
 * spreadsheet tolerates, so its header stays a header and the README carries the
 * sentences (the siblings made the same call for the same reason). CSV is the
 * format you open in a spreadsheet; JSON is the one you keep.
 */
object ExportDocuments {

    /** Bumped only if the shape changes in a way a reader would notice. */
    const val SCHEMA_VERSION = 1

    const val JSON_MIME = "application/json"
    const val CSV_MIME = "text/csv"

    /** How the covered days of a skip window are recovered from one row. */
    const val SKIP_WINDOW_RULE: String =
        "a skip row with `until` covers every logical day from `date` to `until` " +
            "inclusive; those days are not stored as rows"

    const val QUOTA_RULE: String =
        "a quota test passes or fails its ISO week, never a single day"

    const val NO_RUN_RULE: String =
        "a logical day with no row in `days` was never seen by the app: no commit, " +
            "out of every denominator, neutral for health, and it breaks a streak"

    /**
     * The files one command writes. JSON is a single document because it can
     * nest; CSV is one table per file — the suite, the checks and the days are
     * three different rows, and pretending otherwise is what makes an exported
     * spreadsheet useless.
     */
    fun files(bundle: ExportBundle, format: ExportFormat): List<ExportFile> {
        val stamp = bundle.logicalDate
        return when (format) {
            ExportFormat.JSON -> listOf(
                ExportFile("thabit-export-$stamp.json", JSON_MIME, json(bundle))
            )
            ExportFormat.CSV -> listOf(
                ExportFile("thabit-suite-$stamp.csv", CSV_MIME, suiteCsv(bundle)),
                ExportFile("thabit-checks-$stamp.csv", CSV_MIME, checksCsv(bundle)),
                ExportFile("thabit-days-$stamp.csv", CSV_MIME, daysCsv(bundle))
            )
        }
    }

    fun json(bundle: ExportBundle): String = buildString {
        appendLine("{")
        appendLine("""  "app": "thabit",""")
        appendLine("""  "schema": $SCHEMA_VERSION,""")
        appendLine("""  "exported_at": "${isoInstant(bundle.exportedAtMillis)}",""")
        appendLine("""  "timezone": "${escape(bundle.zone.id)}",""")
        appendLine("""  "logical_date": "${bundle.logicalDate}",""")
        appendLine("""  "day_ends": "${CodeFormat.time(bundle.dayEnds)}",""")
        appendLine("""  "week_starts": "${bundle.weekStartsOn.name.lowercase(Locale.ROOT)}",""")
        appendLine("""  "rules": {""")
        appendLine("""    "health": "${escape(Health.FORMULA)}",""")
        appendLine("""    "regression": "${escape(Regressions.RULE)}",""")
        appendLine("""    "flaky": "${escape(FlakyTests.RULE)}",""")
        appendLine("""    "quota": "${escape(QUOTA_RULE)}",""")
        appendLine("""    "skip_window": "${escape(SKIP_WINDOW_RULE)}",""")
        appendLine("""    "no_run": "${escape(NO_RUN_RULE)}"""")
        appendLine("""  },""")
        appendArray("tests", bundle.habits.map(::habitObject))
        appendLine(",")
        appendArray("checks", bundle.checks.map(::checkObject))
        appendLine(",")
        appendArray("days", bundle.days.map { dayObject(it, bundle.zone) })
        appendLine()
        appendLine("}")
    }

    fun suiteCsv(bundle: ExportBundle): String = buildString {
        appendLine(
            "id,name,type,when,assert_unit,assert_target,assert_step," +
                "remind,emoji,position,created_at,archived_at"
        )
        bundle.habits.forEach { habit ->
            appendLine(
                listOf(
                    habit.id.toString(),
                    habit.name,
                    habit.type.name.lowercase(Locale.ROOT),
                    habit.schedule.format(),
                    habit.assert?.unit.orEmpty(),
                    habit.assert?.let { CodeFormat.number(it.target) }.orEmpty(),
                    habit.assert?.let { CodeFormat.number(it.step) }.orEmpty(),
                    habit.remindAt?.let(CodeFormat::time).orEmpty(),
                    habit.emoji.orEmpty(),
                    habit.position.toString(),
                    habit.createdAt.toString(),
                    habit.archivedAt?.toString().orEmpty()
                ).joinToString(",", transform = ::cell)
            )
        }
    }

    fun checksCsv(bundle: ExportBundle): String = buildString {
        appendLine("test_id,date,state,value,note,at,until")
        bundle.checks.forEach { check ->
            appendLine(
                listOf(
                    check.habitId.toString(),
                    check.date.toString(),
                    check.state.name.lowercase(Locale.ROOT),
                    check.value?.let(CodeFormat::number).orEmpty(),
                    check.note.orEmpty(),
                    check.at?.let(CodeFormat::time).orEmpty(),
                    check.until?.toString().orEmpty()
                ).joinToString(",", transform = ::cell)
            )
        }
    }

    fun daysCsv(bundle: ExportBundle): String = buildString {
        appendLine("date,first_seen,amended")
        bundle.days.forEach { day ->
            appendLine(
                listOf(
                    day.date.toString(),
                    isoLocal(day.firstSeen, bundle.zone),
                    day.amended.toString()
                ).joinToString(",", transform = ::cell)
            )
        }
    }

    /** One record per line: an archive reads better, and diffs better, that way. */
    private fun StringBuilder.appendArray(key: String, records: List<String>) {
        if (records.isEmpty()) {
            append("""  "$key": []""")
            return
        }
        appendLine("""  "$key": [""")
        records.forEachIndexed { index, record ->
            append("    ")
            append(record)
            if (index != records.lastIndex) append(",")
            appendLine()
        }
        append("  ]")
    }

    private fun habitObject(habit: Habit): String = listOf(
        """"id": ${habit.id}""",
        """"name": "${escape(habit.name)}"""",
        """"type": "${habit.type.name.lowercase(Locale.ROOT)}"""",
        """"when": "${escape(habit.schedule.format())}"""",
        // The assertion travels whole or not at all: a target without its unit
        // is a number nobody can read back.
        """"assert": ${
            habit.assert?.let {
                """{ "unit": "${escape(it.unit)}", "target": ${CodeFormat.number(it.target)}, """ +
                    """"step": ${CodeFormat.number(it.step)} }"""
            } ?: "null"
        }""",
        """"remind": ${habit.remindAt?.let { "\"${CodeFormat.time(it)}\"" } ?: "null"}""",
        """"emoji": ${habit.emoji?.let { "\"${escape(it)}\"" } ?: "null"}""",
        """"position": ${habit.position}""",
        """"created_at": "${habit.createdAt}"""",
        // Archived tests are in the archive, with the day they left the suite:
        // `[rm]` removes a test from today, never from the history it earned.
        """"archived_at": ${habit.archivedAt?.let { "\"$it\"" } ?: "null"}"""
    ).joinToString(", ", prefix = "{ ", postfix = " }")

    private fun checkObject(check: Check): String = listOf(
        """"test_id": ${check.habitId}""",
        """"date": "${check.date}"""",
        """"state": "${check.state.name.lowercase(Locale.ROOT)}"""",
        """"value": ${check.value?.let(CodeFormat::number) ?: "null"}""",
        """"note": ${check.note?.let { "\"${escape(it)}\"" } ?: "null"}""",
        """"at": ${check.at?.let { "\"${CodeFormat.time(it)}\"" } ?: "null"}""",
        """"until": ${check.until?.let { "\"$it\"" } ?: "null"}"""
    ).joinToString(", ", prefix = "{ ", postfix = " }")

    private fun dayObject(day: DayPresence, zone: ZoneId): String = listOf(
        """"date": "${day.date}"""",
        """"first_seen": "${isoLocal(day.firstSeen, zone)}"""",
        // Permanent by design: taking an amendment back does not take the marker
        // back, because the history WAS edited (VISION §4.2).
        """"amended": ${day.amended}"""
    ).joinToString(", ", prefix = "{ ", postfix = " }")

    private fun isoInstant(millis: Long): String = DateTimeFormatter.ISO_INSTANT
        .format(Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS))

    /** Wall time with its offset: when somebody showed up is a local fact. */
    private fun isoLocal(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(millis).atZone(zone).truncatedTo(ChronoUnit.SECONDS)
        )

    /**
     * Habit names and notes are the user's own words, so unlike the siblings'
     * machine-made cells these really can contain a comma, a quote or a newline.
     */
    private fun cell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
