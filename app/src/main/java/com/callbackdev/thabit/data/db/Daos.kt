package com.callbackdev.thabit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    /** The whole suite, archived tests included — the history needs them. */
    @Query("SELECT * FROM habit ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit ORDER BY position ASC, id ASC")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habit WHERE archived_at IS NULL ORDER BY position ASC, id ASC")
    fun observeLive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun byId(id: Long): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    /** `[rm]` archives; it never deletes. The past runs stay in every committed day. */
    @Query("UPDATE habit SET archived_at = :date WHERE id = :id")
    suspend fun archive(id: Long, date: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habit")
    suspend fun nextPosition(): Int
}

@Dao
interface CheckDao {

    @Query("SELECT * FROM `check` WHERE date = :date")
    fun observeOn(date: String): Flow<List<CheckEntity>>

    @Query("SELECT * FROM `check` WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: String, to: String): Flow<List<CheckEntity>>

    @Query("SELECT * FROM `check` WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun between(from: String, to: String): List<CheckEntity>

    @Query("SELECT * FROM `check` ORDER BY date ASC")
    suspend fun all(): List<CheckEntity>

    @Query("SELECT * FROM `check` WHERE habit_id = :habitId AND date = :date")
    suspend fun find(habitId: Long, date: String): CheckEntity?

    /** A second tap on the same day replaces the first: one fact, one row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(check: CheckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(checks: List<CheckEntity>)

    /** Undo — today is the working tree and it is *supposed* to change. */
    @Query("DELETE FROM `check` WHERE habit_id = :habitId AND date = :date")
    suspend fun delete(habitId: Long, date: String)

    /**
     * Skip windows that started before the range and still reach into it.
     *
     * A `[~ skip] until:` is one row on the day it was tapped, so a window opened
     * last Friday for a week away is invisible to a plain range query on this
     * week — and those days would silently come back as pending. Loaded
     * separately and merged into the read model instead.
     */
    @Query(
        "SELECT * FROM `check` WHERE state = 'SKIP' AND skip_until IS NOT NULL " +
            "AND date < :from AND skip_until >= :from"
    )
    fun observeOpenSkipWindows(from: String): Flow<List<CheckEntity>>

    @Query(
        "SELECT * FROM `check` WHERE state = 'SKIP' AND skip_until IS NOT NULL " +
            "AND date < :from AND skip_until >= :from"
    )
    suspend fun openSkipWindows(from: String): List<CheckEntity>

    @Query("SELECT MIN(date) FROM `check`")
    suspend fun earliestDate(): String?
}

@Dao
interface DayDao {

    @Query("SELECT * FROM day WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(from: String, to: String): Flow<List<DayEntity>>

    @Query("SELECT * FROM day WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun between(from: String, to: String): List<DayEntity>

    @Query("SELECT * FROM day ORDER BY date ASC")
    suspend fun all(): List<DayEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM day WHERE date = :date)")
    suspend fun exists(date: String): Boolean

    /**
     * Presence is written once per logical day: IGNORE, never REPLACE, so
     * `first_seen` keeps the *first* interaction and a second app open at
     * midday cannot quietly rewrite when the user showed up.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markPresent(day: DayEntity)
}
