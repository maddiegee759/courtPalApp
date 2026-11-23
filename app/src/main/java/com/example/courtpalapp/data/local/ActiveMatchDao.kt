package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveMatchDao {

    @Insert
    suspend fun insertMatch(match: ActiveMatchEntity): Long

    @Query("""
        SELECT * FROM active_matches
        WHERE calendarEventId = :eventId
        LIMIT 1
    """)
    suspend fun getMatchByCalendarEvent(
        eventId: Long
    ): ActiveMatchEntity?

    @Query("""
        SELECT * FROM active_matches
        WHERE ownerUserId = :userId
        ORDER BY lastUpdated DESC
    """)
    fun getActiveMatchesForUser(userId: Long): Flow<List<ActiveMatchEntity>>

    @Query("""
        SELECT * FROM active_matches
        WHERE id = :matchId
          AND ownerUserId = :userId
        LIMIT 1
    """)
    suspend fun getMatchByIdForUser(matchId: Long, userId: Long): ActiveMatchEntity?

    @Query("""
        DELETE FROM active_matches
        WHERE id = :matchId
          AND ownerUserId = :userId
    """)
    suspend fun deleteMatchForUser(matchId: Long, userId: Long): Int

    @Query("""
        UPDATE active_matches
        SET scoreJson = :scoreJson,
            historyJson = :historyJson,
            rallyShots = :rallyShots,
            lastUpdated = :lastUpdated
        WHERE id = :matchId
          AND ownerUserId = :ownerUserId
    """)
    suspend fun updateProgressForUser(
        matchId: Long,
        ownerUserId: Long,
        scoreJson: String,
        historyJson: String,
        rallyShots: Int,
        lastUpdated: Long
    ): Int
}
