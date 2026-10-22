package com.example.madverse

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.madverse.core.database.MADVerseDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class RoomMigrationTest {

    @Test
    fun migration_1_2_hasCorrectVersionRange() {
        assertEquals(1, MADVerseDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, MADVerseDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration_1_2_executesAllNonDestructiveSchemaTransformations() {
        val executedSqlStatements = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                val sql = args[0] as String
                executedSqlStatements.add(sql.trim())
            }
            null
        } as SupportSQLiteDatabase

        // Execute Migration 1 -> 2
        MADVerseDatabase.MIGRATION_1_2.migrate(dbProxy)

        // 1. Verify favorite_movies migration
        val favCreate = executedSqlStatements.find { it.contains("CREATE TABLE IF NOT EXISTS `favorite_movies_new`") }
        assertTrue("favorite_movies_new table creation missing", favCreate != null)
        assertTrue("Composite primary key missing in favorite_movies_new", favCreate!!.contains("PRIMARY KEY(`movie_id`, `user_id`)"))

        val favInsert = executedSqlStatements.find { it.contains("INSERT INTO `favorite_movies_new`") }
        assertTrue("favorite_movies data copy missing", favInsert != null)
        assertTrue("Guest attribution missing in favorite_movies copy", favInsert!!.contains("'guest'"))

        assertTrue("favorite_movies drop missing", executedSqlStatements.any { it == "DROP TABLE `favorite_movies`" })
        assertTrue("favorite_movies rename missing", executedSqlStatements.any { it == "ALTER TABLE `favorite_movies_new` RENAME TO `favorite_movies`" })

        // 2. Verify watchlist_movies migration
        val watchCreate = executedSqlStatements.find { it.contains("CREATE TABLE IF NOT EXISTS `watchlist_movies_new`") }
        assertTrue("watchlist_movies_new table creation missing", watchCreate != null)
        assertTrue("Composite primary key missing in watchlist_movies_new", watchCreate!!.contains("PRIMARY KEY(`movie_id`, `user_id`)"))

        val watchInsert = executedSqlStatements.find { it.contains("INSERT INTO `watchlist_movies_new`") }
        assertTrue("watchlist_movies data copy missing", watchInsert != null)
        assertTrue("Guest attribution missing in watchlist_movies copy", watchInsert!!.contains("'guest'"))

        assertTrue("watchlist_movies drop missing", executedSqlStatements.any { it == "DROP TABLE `watchlist_movies`" })
        assertTrue("watchlist_movies rename missing", executedSqlStatements.any { it == "ALTER TABLE `watchlist_movies_new` RENAME TO `watchlist_movies`" })

        // 3. Verify user_preference_profile migration
        val prefCreate = executedSqlStatements.find { it.contains("CREATE TABLE IF NOT EXISTS `user_preference_profile_new`") }
        assertTrue("user_preference_profile_new table creation missing", prefCreate != null)
        assertTrue("user_id primary key missing in user_preference_profile_new", prefCreate!!.contains("PRIMARY KEY(`user_id`)"))

        val prefInsert = executedSqlStatements.find { it.contains("INSERT INTO `user_preference_profile_new`") }
        assertTrue("user_preference_profile data copy missing", prefInsert != null)
        assertTrue("Guest attribution missing in user_preference_profile copy", prefInsert!!.contains("'guest'"))

        assertTrue("user_preference_profile drop missing", executedSqlStatements.any { it == "DROP TABLE `user_preference_profile`" })
        assertTrue("user_preference_profile rename missing", executedSqlStatements.any { it == "ALTER TABLE `user_preference_profile_new` RENAME TO `user_preference_profile`" })

        // 4. Verify pending_sync_actions table creation
        val syncQueueCreate = executedSqlStatements.find { it.contains("CREATE TABLE IF NOT EXISTS `pending_sync_actions`") }
        assertTrue("pending_sync_actions creation missing", syncQueueCreate != null)
        assertTrue("action_type missing in pending_sync_actions", syncQueueCreate!!.contains("`action_type` TEXT NOT NULL"))
    }
}
