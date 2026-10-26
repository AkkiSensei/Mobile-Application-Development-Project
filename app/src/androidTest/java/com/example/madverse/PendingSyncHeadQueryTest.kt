package com.example.madverse

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.madverse.core.database.MADVerseDatabase
import com.example.madverse.core.database.entity.PendingSyncEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PendingSyncHeadQueryTest {
    @Test fun queueHeadUsesDurableIdAndUserScopeAndExactAcknowledgement() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MADVerseDatabase::class.java).build()
        try {
            val dao = db.pendingSyncDao()
            fun row(id: Long, uid: String, time: Long) = PendingSyncEntity(id = id, userId = uid,
                actionType = "DELETE", entityType = "FAVORITE", itemId = "1", createdAt = time)
            dao.insertAction(row(100, "A", 1))
            dao.insertAction(row(50, "A", 999))
            dao.insertAction(row(1, "B", 0))
            assertEquals(50L, dao.getNextPendingActionForUser("A")!!.id)
            dao.deleteActionById(50)
            assertEquals(100L, dao.getNextPendingActionForUser("A")!!.id)
            assertEquals(1L, dao.getNextPendingActionForUser("B")!!.id)
            dao.deleteActionById(100)
            assertNull(dao.getNextPendingActionForUser("A"))
            assertEquals(1, dao.getPendingActionsForUser("B").size)
        } finally { db.close() }
    }
}
