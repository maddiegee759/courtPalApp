package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveMatchDao {

    @Insert
    suspend fun insertMatch(match: ActiveMatchEntity): Long

    @Update
    suspend fun updateMatch(match: ActiveMatchEntity)

    @Query("""
        SELECT * FROM active_matches
        ORDER BY lastUpdated DESC
    """)
    fun getAllActiveMatches(): Flow<List<ActiveMatchEntity>>

    @Query("""
        SELECT * FROM active_matches
        WHERE id = :matchId LIMIT 1
    """)
    suspend fun getMatchById(matchId: Long): ActiveMatchEntity?

    @Query("""
        SELECT * FROM active_matches
        WHERE calendarEventId = :eventId
        LIMIT 1
    """)
    suspend fun getMatchByCalendarEvent(
        eventId: Long
    ): ActiveMatchEntity?

    @Query("""
        DELETE FROM active_matches
        WHERE id = :matchId
    """)
    suspend fun deleteMatch(matchId: Long)
}
