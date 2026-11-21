package com.example.courtpalapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CalendarEventEntity::class,
        FriendActivityEntity::class,
        MatchEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun friendActivityDao(): FriendActivityDao
    abstract fun matchDao(): MatchDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS friend_activities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER,
                        friendName TEXT NOT NULL,
                        description TEXT NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS matches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        player1Name TEXT NOT NULL,
                        player2Name TEXT NOT NULL,
                        player2Id TEXT,
                        winner INTEGER NOT NULL,
                        player1Sets INTEGER NOT NULL,
                        player2Sets INTEGER NOT NULL,
                        player1Games INTEGER NOT NULL,
                        player2Games INTEGER NOT NULL,
                        player1TotalPoints INTEGER NOT NULL,
                        player2TotalPoints INTEGER NOT NULL,
                        longestRally INTEGER NOT NULL,
                        averageRally REAL NOT NULL,
                        bestOfSets INTEGER NOT NULL,
                        gamesPerSet INTEGER NOT NULL,
                        playedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            ALTER TABLE matches
            ADD COLUMN completedSetsJson TEXT NOT NULL DEFAULT '[]'
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE matches
            ADD COLUMN scoringMode TEXT NOT NULL DEFAULT 'ADVANTAGE'
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE matches
            ADD COLUMN tieBreakEnabled INTEGER NOT NULL DEFAULT 1
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE matches
            ADD COLUMN tieBreakPoints INTEGER NOT NULL DEFAULT 7
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE matches
            ADD COLUMN trackRallies INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE matches
            ADD COLUMN matchMode TEXT NOT NULL DEFAULT 'LIVE'
            """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
            ALTER TABLE calendar_events
            ADD COLUMN friendName TEXT
            """.trimIndent()
                )

                // Existing friendId values contain names,
                // so preserve them in the new display-name field.
                db.execSQL(
                    """
            UPDATE calendar_events
            SET friendName = friendId
            WHERE friendId IS NOT NULL
            """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN proposedTitle TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN proposedDate TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN proposedStartTime TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN proposedEndTime TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN proposedLocation TEXT")
            }
        }
    }
}
