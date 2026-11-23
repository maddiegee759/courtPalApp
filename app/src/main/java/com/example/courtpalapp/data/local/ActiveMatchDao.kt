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

    @Query("""
        DELETE FROM active_matches
        WHERE ownerUserId = :userId
    """)
    suspend fun deleteActiveMatchesForUser(userId: Long): Int

    @Query("""
    SELECT * FROM active_matches
    WHERE ownerUserId IS NOT NULL
      AND ownerUserId != :deletedUserId
""")
    suspend fun getActiveMatchesOwnedByOthers(
        deletedUserId: Long
    ): List<ActiveMatchEntity>


    @Query("""
        UPDATE active_matches
        SET settingsJson = :settingsJson,
            player1Name = :player1Name,
            player2Name = :player2Name
        WHERE id = :matchId
          AND ownerUserId = :ownerUserId
    """)
    suspend fun updateAnonymizedActiveMatch(
        matchId: Long,
        ownerUserId: Long,
        settingsJson: String,
        player1Name: String,
        player2Name: String
    ): Int


    @Query("""
        UPDATE active_matches
        SET calendarEventId = NULL
        WHERE ownerUserId IS NOT NULL
          AND ownerUserId != :deletedUserId
          AND calendarEventId IN (
              SELECT id FROM calendar_events
              WHERE ownerUserId = :deletedUserId
                 OR friendId = CAST(:deletedUserId AS TEXT)
          )
    """)
    suspend fun detachActiveMatchesFromDeletedEvents(deletedUserId: Long): Int
}
