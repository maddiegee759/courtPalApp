package com.example.courtpalapp.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val status: String,
    val friendId: String? = null,
    val googleEventId: String? = null,
    val friendName: String? = null,
    val ownerUserId: Long? = null,
    // proposed values are separate so the confirmed event remains unchanged
    val proposedTitle: String? = null,
    val proposedDate: String? = null,
    val proposedStartTime: String? = null,
    val proposedEndTime: String? = null,
    val proposedLocation: String? = null,
    val proposedByUserId: Long? = null,
    val hasStarted: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
