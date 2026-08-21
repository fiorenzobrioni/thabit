package com.callbackdev.thabit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Executor

/**
 * `thabit.db` — three tables, no verdicts.
 *
 * Version 1. Nothing is ever pruned: a decade of checks is a few hundred
 * kilobytes and the user's history belongs to the user (VISION §7).
 *
 * The schema is exported to `app/schemas/` and committed, so every future
 * migration is written against a checked-in description of the previous version
 * instead of against somebody's memory of it.
 */
@Database(
    entities = [HabitEntity::class, CheckEntity::class, DayEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ThabitDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun checkDao(): CheckDao
    abstract fun dayDao(): DayDao

    companion object {
        const val NAME = "thabit.db"

        fun build(context: Context): ThabitDatabase =
            Room.databaseBuilder(context.applicationContext, ThabitDatabase::class.java, NAME)
                // Foreign keys are declared on `check`; SQLite only enforces them
                // when asked, and an orphan check row would be a run with no test.
                .build()

        /**
         * In-memory instance for tests — same schema, no file.
         *
         * [executor], when given, runs both queries and the invalidation
         * tracker, so a test that drives a coroutine scheduler drives Room's
         * `Flow` emissions with it. Without that hook the only way to assert on
         * an observed query is to wait real milliseconds and hope, which is how
         * a suite acquires tests that fail one run in five.
         */
        fun inMemory(context: Context, executor: Executor? = null): ThabitDatabase =
            Room.inMemoryDatabaseBuilder(context, ThabitDatabase::class.java)
                .allowMainThreadQueries()
                .apply {
                    if (executor != null) {
                        setQueryExecutor(executor)
                        setTransactionExecutor(executor)
                    }
                }
                .build()
    }
}
