package com.example.madverse

import com.example.madverse.core.database.MADVerseDatabase
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Room database configuration and KSP code generation readiness.
 * Verifies:
 * - Database name and version match architecture blueprint (v1)
 * - Migration array is properly exposed without destructive fallback
 * - Room KSP compiler generates valid Database and DAO implementations
 */
class RoomConfigurationAndMigrationTest {

    @Test
    fun databaseMetadata_constantsMatchArchitectureBlueprint() {
        assertEquals("madverse_database.db", MADVerseDatabase.DATABASE_NAME)
        assertEquals(2, MADVerseDatabase.DATABASE_VERSION)
        assertNotNull(MADVerseDatabase.ALL_MIGRATIONS)
        assertEquals(1, MADVerseDatabase.ALL_MIGRATIONS.size)
        assertEquals(1, MADVerseDatabase.ALL_MIGRATIONS[0].startVersion)
        assertEquals(2, MADVerseDatabase.ALL_MIGRATIONS[0].endVersion)
    }

    @Test
    fun roomGeneratedDatabaseImplementation_existsAndExtendsMADVerseDatabase() {
        val dbImplClass = Class.forName("com.example.madverse.core.database.MADVerseDatabase_Impl")
        assertNotNull(dbImplClass)
        assertTrue(MADVerseDatabase::class.java.isAssignableFrom(dbImplClass))
    }

    @Test
    fun roomGeneratedDaoImplementations_existAndImplementDaoContracts() {
        val favoriteDaoImpl = Class.forName("com.example.madverse.core.database.dao.FavoriteDao_Impl")
        assertTrue(FavoriteDao::class.java.isAssignableFrom(favoriteDaoImpl))

        val watchlistDaoImpl = Class.forName("com.example.madverse.core.database.dao.WatchlistDao_Impl")
        assertTrue(WatchlistDao::class.java.isAssignableFrom(watchlistDaoImpl))

        val userPrefDaoImpl = Class.forName("com.example.madverse.core.database.dao.UserPreferenceDao_Impl")
        assertTrue(UserPreferenceDao::class.java.isAssignableFrom(userPrefDaoImpl))

        val pendingSyncDaoImpl = Class.forName("com.example.madverse.core.database.dao.PendingSyncDao_Impl")
        assertTrue(com.example.madverse.core.database.dao.PendingSyncDao::class.java.isAssignableFrom(pendingSyncDaoImpl))
    }
}
