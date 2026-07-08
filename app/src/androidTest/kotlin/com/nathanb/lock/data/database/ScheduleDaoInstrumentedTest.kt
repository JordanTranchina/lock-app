package com.nathanb.lock.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nathanb.lock.data.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies ScheduleDao against a real Android SQLite backend (in-memory Room DB),
 * covering the CRUD the scheduled auto-lock feature relies on.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleDaoInstrumentedTest {

    private lateinit var db: LockDatabase
    private lateinit var dao: ScheduleDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LockDatabase::class.java).build()
        dao = db.scheduleDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertReadToggleDelete() = runBlocking {
        val id = dao.insert(
            Schedule(
                startMinuteOfDay = 9 * 60,
                endMinuteOfDay = 17 * 60,
                daysMask = 0b0011111, // weekdays
                profileId = null,
            ),
        )
        assertTrue(id > 0)

        val all = dao.getAllOnce()
        assertEquals(1, all.size)
        assertEquals(9 * 60, all[0].startMinuteOfDay)
        assertEquals(17 * 60, all[0].endMinuteOfDay)
        assertEquals(0b0011111, all[0].daysMask)
        assertTrue(all[0].enabled) // default

        dao.setEnabled(id, false)
        assertFalse(dao.getById(id)!!.enabled)

        dao.delete(dao.getById(id)!!)
        assertTrue(dao.getAllOnce().isEmpty())
    }

    @Test
    fun orderedByStartTime() = runBlocking {
        dao.insert(Schedule(startMinuteOfDay = 22 * 60, endMinuteOfDay = 7 * 60, daysMask = Schedule.ALL_DAYS))
        dao.insert(Schedule(startMinuteOfDay = 6 * 60, endMinuteOfDay = 8 * 60, daysMask = Schedule.ALL_DAYS))

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        assertEquals(6 * 60, all[0].startMinuteOfDay) // earliest first
        assertEquals(22 * 60, all[1].startMinuteOfDay)
    }
}
