package com.example.courtpalapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CalendarEventEntity::class,
        FriendActivityEntity::class,
        MatchEntity::class,
        ActiveMatchEntity::class,
        FriendEntity::class,
        UserEntity::class
    ],
    version = 14,
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration(
                        dropAllTables = true
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
