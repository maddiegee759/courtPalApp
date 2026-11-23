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
        MatchEntity::class,
        ActiveMatchEntity::class,
        FriendEntity::class,
        UserEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun friendActivityDao(): FriendActivityDao
    abstract fun matchDao(): MatchDao
    abstract fun activeMatchDao(): ActiveMatchDao
    abstract fun friendDao(): FriendDao
    abstract fun userDao(): UserDao

    companion object {

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE matches ADD COLUMN ownerUserId INTEGER")
                db.execSQL("ALTER TABLE friend_activities ADD COLUMN ownerUserId INTEGER")
                db.execSQL("ALTER TABLE friend_activities ADD COLUMN badgeId TEXT")
                db.execSQL("ALTER TABLE calendar_events ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_friend_activities_ownerUserId_badgeId
                    ON friend_activities(ownerUserId, badgeId)
                """.trimIndent())

                // Existing records cannot safely be attributed if several
                // accounts share this device. A sole account is unambiguous.
                db.execSQL("""
                    UPDATE matches
                    SET ownerUserId = (SELECT id FROM users LIMIT 1)
                    WHERE (SELECT COUNT(*) FROM users) = 1
                """.trimIndent())
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // Add ownership to unfinished matches.
                db.execSQL(
                    "ALTER TABLE active_matches ADD COLUMN ownerUserId INTEGER"
                )

                // If only one account exists, old matches can safely
                // be assigned to that account.
                db.execSQL("""
            UPDATE active_matches
            SET ownerUserId = (SELECT id FROM users LIMIT 1)
            WHERE (SELECT COUNT(*) FROM users) = 1
        """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
