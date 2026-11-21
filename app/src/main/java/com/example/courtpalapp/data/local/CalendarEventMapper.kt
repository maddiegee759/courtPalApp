
package com.example.courtpalapp.data.local

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.courtpalapp.feature.calendar.CalendarEvent
import com.example.courtpalapp.feature.calendar.EventStatus
import java.time.LocalDate

@RequiresApi(Build.VERSION_CODES.O)
fun CalendarEventEntity.toCalendarEvent(): CalendarEvent {
    return CalendarEvent(
        title = title,
        date = LocalDate.parse(date),
        startTime = startTime,
        endTime = endTime,
        location = location,
        status = EventStatus.valueOf(status)
    )
}

fun CalendarEvent.toEntity(
    friendId: String? = null,
    friendName: String? = null
): CalendarEventEntity {
    return CalendarEventEntity(
        title = title,
        date = date.toString(),
        startTime = startTime,
        endTime = endTime,
        location = location,
        status = status.name,
        friendId = friendId,
        friendName = friendName
    )
}
