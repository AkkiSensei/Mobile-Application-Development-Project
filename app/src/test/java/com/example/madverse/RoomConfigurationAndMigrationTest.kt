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
        assertEquals(1, MADVerseDatabase.DATABASE_VERSION)
        assertNotNull(MADVerseDatabase.ALL_MIGRATIONS)
        assertEquals(0, MADVerseDatabase.ALL_MIGRATIONS.size)
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
    }
}
