package com.callbackdev.thabit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The three tables of thabit, and there will never be a fourth for verdicts.
 *
 * `habit` is the suite, `check` is what the user typed, `day` is the evidence
 * that they were there. Build results, streaks, health, coverage and records are
 * computed on read from these rows (VISION §6.8) — no snapshot table, no
 * midnight mutation to get wrong, and `--amend` recomputes everything for free.
 *
 * Every date is stored as an ISO-8601 string rather than an epoch number: it
 * sorts lexicographically (so range queries work), it is readable in a database
 * dump, and it is byte-for-byte what the JSON and CSV exports write, so the
 * user's file and the app's storage never disagree about what a day is called.
 */
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** [com.callbackdev.thabit.domain.model.HabitType] name: BOOLEAN, COUNTER, AVOID. */
    val type: String,
    @ColumnInfo(name = "assert_target") val assertTarget: Double? = null,
    @ColumnInfo(name = "assert_unit") val assertUnit: String? = null,
    @ColumnInfo(name = "assert_step") val assertStep: Double? = null,
    /** Canonical schedule string: `daily`, `mon,wed,fri`, `3/week`, `every 2d`. */
    val schedule: String,
    /** `HH:mm` local time, or null for no reminder. */
    @ColumnInfo(name = "remind_at") val remindAt: String? = null,
    val emoji: String? = null,
    val position: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null
)

/**
 * One test's result on one logical day. One row per test per day, written by a
 * tap and by nothing else — the absence of a row is what "pending" means.
 *
 * The primary key is the pair, so a second tap on the same day replaces the
 * first instead of stacking a second opinion on the same fact.
 */
@Entity(
    tableName = "check",
    primaryKeys = ["habit_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("date"), Index("habit_id")]
)
data class CheckEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    /** Logical date, ISO-8601. Never relabelled: the past keeps the rules of its time. */
    val date: String,
    /** [com.callbackdev.thabit.domain.model.CheckState] name: PASS, FAIL, SKIP. */
    val state: String,
    val value: Double? = null,
    val note: String? = null,
    /** `HH:mm` wall-clock stamp of the tap — the `# 07:12` in the file. */
    @ColumnInfo(name = "at") val at: String? = null,
    /** Skips only: the last logical day the skip window covered. */
    @ColumnInfo(name = "skip_until") val until: String? = null
)

/**
 * Presence: this logical day was deliberately used.
 *
 * Written once per day by the first deliberate interaction — app open, widget
 * tap, notification action — and never by an automatic event (VISION §7). Its
 * absence is the whole reason the app can say `no run` instead of inventing a
 * red build for a day nobody was there.
 */
@Entity(tableName = "day")
data class DayEntity(
    @PrimaryKey val date: String,
    /** Epoch millis of that first interaction. */
    @ColumnInfo(name = "first_seen") val firstSeen: Long
)
