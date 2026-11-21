package com.example.courtpalapp.feature.home

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.courtpalapp.data.local.CalendarEventEntity
import com.example.courtpalapp.feature.calendar.EventStatus
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration

@RequiresApi(Build.VERSION_CODES.O)
fun getUpcomingMatches(
    events: List<CalendarEventEntity>,
    now: LocalDateTime = LocalDateTime.now()
): List<CalendarEventEntity> {

    return events.filter { event ->

        if (
            event.status != EventStatus.CONFIRMED.name ||
            event.hasStarted
        ) {
            return@filter false
        }

        val matchStart = try {
            LocalDateTime.of(
                LocalDate.parse(event.date),
                LocalTime.parse(event.startTime)
            )
        } catch (e: Exception) {
            return@filter false
        }

        val hoursUntilStart = Duration.between(
            now,
            matchStart
        )

        hoursUntilStart >= Duration.ofHours(-6) &&
                hoursUntilStart <= Duration.ofHours(6)

    }.sortedWith(
        compareBy(
            { it.date },
            { it.startTime }
        )
    )
}
