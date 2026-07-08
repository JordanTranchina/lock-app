package com.nathanb.lock.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nathanb.lock.data.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises MIGRATION_4_5 on a real Android device/emulator.
 *
 * A v4 database (schema from the committed `4.json`) is seeded, then reopened through
 * the real Room stack with MIGRATION_4_5 applied. Room validates the migrated schema
 * against the compiled entities and throws if they disagree — so a successful open is
 * proof the migration matches the `Schedule` entity. We then confirm pre-existing data
 * survived and the new `schedules` table works via its DAO.
 */
@RunWith(AndroidJUnit4::class)
class LockDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LockDatabase::class.java,
    )

    @Test
    fun migrate4To5_opensAndPreservesData() {
        // Seed a v4 database with an existing profile row.
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO profiles (id, name, blockedPackages, type, isDefault, durationMs) " +
                    "VALUES (1, 'Work', '[]', 'standard', 1, NULL)",
            )
            close()
        }

        // Reopen through Room with the migration applied. Room validates the resulting
        // schema against the compiled entities (throws IllegalStateException on mismatch).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, LockDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_4_5)
            .build()
        try {
            runBlocking {
                // Pre-existing data survived the migration.
                assertEquals("Work", db.profileDao().getById(1)!!.name)

                // The new schedules table is usable via its DAO.
                val id = db.scheduleDao().insert(
                    Schedule(startMinuteOfDay = 1320, endMinuteOfDay = 420, daysMask = Schedule.ALL_DAYS),
                )
                assertTrue(id > 0)
                assertEquals(1, db.scheduleDao().getAllOnce().size)
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }
}
