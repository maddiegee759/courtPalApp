
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


    @Query("""
    UPDATE calendar_events
    SET googleEventId = :googleEventId
    WHERE id = :eventId
""")
    suspend fun updateGoogleEventId(
        eventId: Long,
        googleEventId: String
    )

    @Query("""
    SELECT googleEventId
    FROM calendar_events
    WHERE id = :eventId
""")
    suspend fun getGoogleEventId(eventId: Long): String?


    @Query("""
    UPDATE calendar_events
    SET status = :status
    WHERE id = :eventId
""")
    suspend fun updateEventStatus(
        eventId: Long,
        status: String
    )
}
