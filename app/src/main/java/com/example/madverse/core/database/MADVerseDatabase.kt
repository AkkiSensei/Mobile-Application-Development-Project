package com.example.madverse.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.madverse.core.database.dao.FavoriteDao
import com.example.madverse.core.database.dao.PendingSyncDao
import com.example.madverse.core.database.dao.UserPreferenceDao
import com.example.madverse.core.database.dao.WatchlistDao
import com.example.madverse.core.database.entity.FavoriteEntity
import com.example.madverse.core.database.entity.PendingSyncEntity
import com.example.madverse.core.database.entity.UserPreferenceEntity
import com.example.madverse.core.database.entity.WatchlistEntity

@Database(
    entities = [
        WatchlistEntity::class,
        FavoriteEntity::class,
        UserPreferenceEntity::class,
        PendingSyncEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MADVerseDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        const val DATABASE_NAME = "madverse_database.db"
        const val DATABASE_VERSION = 2

        /**
         * Clean Room migrations registry.
         * Ensures non-destructive schema evolution so user favorites, watchlist records,
         * and quiz preferences are never dropped or wiped.
         *
         * Migration 1 -> 2:
         * - Preserves all existing local data by attributing existing records to 'guest'.
         * - Introduces composite primary key (movie_id, user_id) for favorite_movies and watchlist_movies.
         * - Changes user_preference_profile primary key from singleton int id to user_id.
         * - Creates pending_sync_actions table for resilient offline-to-cloud synchronization.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Migrate favorite_movies
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `favorite_movies_new` (
                        `movie_id` INTEGER NOT NULL,
                        `user_id` TEXT NOT NULL DEFAULT 'guest',
                        `title` TEXT NOT NULL,
                        `poster_path` TEXT,
                        `backdrop_path` TEXT,
                        `release_date` TEXT,
                        `vote_average` REAL NOT NULL,
                        `overview` TEXT,
                        `saved_timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`movie_id`, `user_id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `favorite_movies_new` (`movie_id`, `user_id`, `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `saved_timestamp`)
                    SELECT `movie_id`, 'guest', `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `saved_timestamp`
                    FROM `favorite_movies`
                """.trimIndent())
                db.execSQL("DROP TABLE `favorite_movies`")
                db.execSQL("ALTER TABLE `favorite_movies_new` RENAME TO `favorite_movies`")

                // 2. Migrate watchlist_movies
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `watchlist_movies_new` (
                        `movie_id` INTEGER NOT NULL,
                        `user_id` TEXT NOT NULL DEFAULT 'guest',
                        `title` TEXT NOT NULL,
                        `poster_path` TEXT,
                        `backdrop_path` TEXT,
                        `release_date` TEXT,
                        `vote_average` REAL NOT NULL,
                        `overview` TEXT,
                        `saved_timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`movie_id`, `user_id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `watchlist_movies_new` (`movie_id`, `user_id`, `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `saved_timestamp`)
                    SELECT `movie_id`, 'guest', `title`, `poster_path`, `backdrop_path`, `release_date`, `vote_average`, `overview`, `saved_timestamp`
                    FROM `watchlist_movies`
                """.trimIndent())
                db.execSQL("DROP TABLE `watchlist_movies`")
                db.execSQL("ALTER TABLE `watchlist_movies_new` RENAME TO `watchlist_movies`")

                // 3. Migrate user_preference_profile
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_preference_profile_new` (
                        `user_id` TEXT NOT NULL DEFAULT 'guest',
                        `genre_ids_csv` TEXT NOT NULL,
                        `mood` TEXT NOT NULL,
                        `era` TEXT NOT NULL,
                        `runtime_preference` TEXT NOT NULL,
                        `preferred_language` TEXT,
                        `min_rating_threshold` REAL NOT NULL,
                        `updated_timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`user_id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `user_preference_profile_new` (`user_id`, `genre_ids_csv`, `mood`, `era`, `runtime_preference`, `preferred_language`, `min_rating_threshold`, `updated_timestamp`)
                    SELECT 'guest', `genre_ids_csv`, `mood`, `era`, `runtime_preference`, `preferred_language`, `min_rating_threshold`, `updated_timestamp`
                    FROM `user_preference_profile`
                """.trimIndent())
                db.execSQL("DROP TABLE `user_preference_profile`")
                db.execSQL("ALTER TABLE `user_preference_profile_new` RENAME TO `user_preference_profile`")

                // 4. Create pending_sync_actions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_sync_actions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `user_id` TEXT NOT NULL,
                        `action_type` TEXT NOT NULL,
                        `entity_type` TEXT NOT NULL,
                        `item_id` TEXT NOT NULL,
                        `payload_json` TEXT,
                        `created_at` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
