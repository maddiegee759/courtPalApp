package com.example.courtpalapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY date, startTime")
    fun getAllEvents(): Flow<List<CalendarEventEntity>>

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
    @Query("""UPDATE calendar_events SET
        proposedTitle = :title, proposedDate = :date,
        proposedStartTime = :startTime, proposedEndTime = :endTime,
        proposedLocation = :location
        WHERE id = :eventId AND status = 'CONFIRMED' AND proposedTitle IS NULL""")
    suspend fun submitAmendment(eventId: Long, title: String, date: String,
                                startTime: String, endTime: String, location: String): Int

    @Query("""UPDATE calendar_events SET
        title = proposedTitle, date = proposedDate,
        startTime = proposedStartTime, endTime = proposedEndTime,
        location = proposedLocation,
        proposedTitle = NULL, proposedDate = NULL,
        proposedStartTime = NULL, proposedEndTime = NULL,
        proposedLocation = NULL
        WHERE id = :eventId AND status = 'CONFIRMED' AND proposedTitle IS NOT NULL""")
    suspend fun acceptAmendment(eventId: Long): Int

    @Query("""UPDATE calendar_events SET
        proposedTitle = NULL, proposedDate = NULL,
        proposedStartTime = NULL, proposedEndTime = NULL,
        proposedLocation = NULL
        WHERE id = :eventId AND proposedTitle IS NOT NULL""")
    suspend fun declineAmendment(eventId: Long): Int
}
