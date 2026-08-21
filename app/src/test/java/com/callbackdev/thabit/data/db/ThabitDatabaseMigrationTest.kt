package com.callbackdev.thabit.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The v1 → v2 migration, run against a real v1 table.
 *
 * A migration nobody has executed is a guess, and the thing it would break is
 * the one thing this app must never break: the user's own history. The v1
 * `CREATE TABLE` below is copied from the committed `app/schemas/…/1.json`,
 * which is exactly why that file is in the repository (CLAUDE.md).
 */
@RunWith(RobolectricTestRunner::class)
class ThabitDatabaseMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `v1 to v2 adds the amend marker and keeps every row it found`() {
        val file = folder.newFile("v1.db")
        val connection = AndroidSQLiteDriver().open(file.absolutePath)

        // v1, verbatim from the exported schema.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `day` (`date` TEXT NOT NULL, " +
                "`first_seen` INTEGER NOT NULL, PRIMARY KEY(`date`))"
        )
        connection.execSQL("INSERT INTO day (date, first_seen) VALUES ('2026-08-20', 1755600000000)")

        ThabitDatabase.MIGRATION_1_2.migrate(connection)

        connection.prepare("SELECT date, first_seen, amended FROM day").use { statement ->
            assertEquals(true, statement.step())
            assertEquals("2026-08-20", statement.getText(0))
            // The day that was already there keeps its presence...
            assertEquals(1755600000000L, statement.getLong(1))
            // ...and starts out not amended, because it was not.
            assertEquals(0L, statement.getLong(2))
            assertEquals(false, statement.step())
        }
        connection.close()
    }
}
