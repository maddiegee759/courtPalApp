package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity): Long

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): CalendarEventEntity?

    @Query("UPDATE calendar_events SET googleEventId = :googleEventId WHERE id = :eventId")
    suspend fun updateGoogleEventId(eventId: Long, googleEventId: String)

    @Query("SELECT googleEventId FROM calendar_events WHERE id = :eventId")
    suspend fun getGoogleEventId(eventId: Long): String?

    @Query("UPDATE calendar_events SET status = :status WHERE id = :eventId")
    suspend fun updateEventStatus(eventId: Long, status: String)

    // Atomic updates prevent losing a proposal when another edit occurs.
    @Query("""
    UPDATE calendar_events SET
        proposedTitle = :title,
        proposedDate = :date,
        proposedStartTime = :startTime,
        proposedEndTime = :endTime,
        proposedLocation = :location,
        proposedByUserId = :currentUserId
    WHERE id = :eventId
      AND status = 'CONFIRMED'
      AND proposedTitle IS NULL
      AND (
          ownerUserId = :currentUserId
          OR friendId = CAST(:currentUserId AS TEXT)
      )
""")
    suspend fun submitAmendment(
        eventId: Long,
        currentUserId: Long,
        title: String,
        date: String,
        startTime: String,
        endTime: String,
        location: String
    ): Int

    @Query("""
        UPDATE calendar_events SET
            title = proposedTitle,
            date = proposedDate,
            startTime = proposedStartTime,
            endTime = proposedEndTime,
            location = proposedLocation,
            proposedTitle = NULL,
            proposedDate = NULL,
            proposedStartTime = NULL,
            proposedEndTime = NULL,
            proposedLocation = NULL,
            proposedByUserId = NULL
        WHERE id = :eventId
          AND status = 'CONFIRMED'
          AND proposedTitle IS NOT NULL
          AND proposedByUserId IS NOT NULL
          AND proposedByUserId != :currentUserId
          AND (
              ownerUserId = :currentUserId
              OR friendId = CAST(:currentUserId AS TEXT)
          )
    """)
    suspend fun acceptAmendment(
        eventId: Long,
        currentUserId: Long
    ): Int

    @Query("""
        UPDATE calendar_events SET
            proposedTitle = NULL,
            proposedDate = NULL,
            proposedStartTime = NULL,
            proposedEndTime = NULL,
            proposedLocation = NULL,
            proposedByUserId = NULL
        WHERE id = :eventId
          AND proposedTitle IS NOT NULL
          AND proposedByUserId IS NOT NULL
          AND proposedByUserId != :currentUserId
          AND (
              ownerUserId = :currentUserId
              OR friendId = CAST(:currentUserId AS TEXT)
          )
    """)
    suspend fun declineAmendment(
        eventId: Long,
        currentUserId: Long
    ): Int

    @Query("UPDATE calendar_events SET hasStarted = 1 WHERE id = :eventId")
    suspend fun markMatchStarted(eventId: Long)

    @Query("SELECT * FROM calendar_events WHERE ownerUserId = :userId OR friendId = CAST(:userId AS TEXT) ORDER BY date, startTime")

    fun getEventsForUser(userId: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE friendId = CAST(:userId AS TEXT) AND ownerUserId != :userId AND status = 'PENDING' ORDER BY date, startTime")

    fun getIncomingProposals(userId: Long): Flow<List<CalendarEventEntity>>

    @Query("""
        UPDATE calendar_events
        SET status = 'CONFIRMED'
        WHERE id = :eventId
          AND friendId = CAST(:currentUserId AS TEXT)
          AND ownerUserId != :currentUserId
          AND status = 'PENDING'
    """)
    suspend fun acceptProposal(eventId: Long, currentUserId: Long): Int

    @Query("""
        UPDATE calendar_events
        SET status = 'DECLINED'
        WHERE id = :eventId
          AND friendId = CAST(:currentUserId AS TEXT)
          AND ownerUserId != :currentUserId
          AND status = 'PENDING'
    """)
    suspend fun declineProposal(eventId: Long, currentUserId: Long): Int

    @Query("""
        UPDATE calendar_events
        SET status = 'CANCELLED'
        WHERE id = :eventId
          AND ownerUserId = :currentUserId
          AND status != 'CANCELLED'
    """)
    suspend fun cancelOwnedEvent(eventId: Long, currentUserId: Long): Int

    @Query("""
        UPDATE calendar_events
        SET title = :title,
            date = :date,
            startTime = :startTime,
            endTime = :endTime,
            location = :location
        WHERE id = :eventId
          AND ownerUserId = :currentUserId
          AND status = 'PENDING'
    """)
    suspend fun updatePendingProposal(
        eventId: Long,
        currentUserId: Long,
        title: String,
        date: String,
        startTime: String,
        endTime: String,
        location: String
    ): Int

    @Query("""
        DELETE FROM calendar_events
        WHERE ownerUserId = :userId
           OR friendId = CAST(:userId AS TEXT)
    """)
    suspend fun deleteEventsForUser(userId: Long): Int
}
