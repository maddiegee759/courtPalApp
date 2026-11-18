
package com.example.courtpalapp.feature.calendar

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class EventStatus {
    CONFIRMED,
    PENDING,
    DECLINED,
    CANCELLED
}
data class CalendarEvent(
    val title: String,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val location: String,
    val status: EventStatus
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarScreen() {

    val today = remember { LocalDate.now() }

    var displayedMonth by remember {
        mutableStateOf(YearMonth.from(today))
    }

    var selectedDate by remember {
        mutableStateOf(today)
    }

    // Temporary sample data - replace with Room later
    val events = remember {
        mutableStateListOf<CalendarEvent>()
    }

    var showAddEventDialog by remember {
        mutableStateOf(false)
    }

    val selectedEvents = events.filter {
        it.date == selectedDate
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddEventDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add event"
                )
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            item {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                MonthCalendar(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    events = events,
                    onDateSelected = { selectedDate = it },
                    onPreviousMonth = {
                        displayedMonth = displayedMonth.minusMonths(1)
                        selectedDate = displayedMonth.atDay(1)
                    },
                    onNextMonth = {
                        displayedMonth = displayedMonth.plusMonths(1)
                        selectedDate = displayedMonth.atDay(1)
                    }
                )
            }

            item {
                Text(
                    text = selectedDate.format(
                        DateTimeFormatter.ofPattern(
                            "EEEE, d MMMM",
                            Locale.UK
                        )
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (selectedEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No events for this date",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(selectedEvents) { event ->
                    EventCard(event)
                }
            }
        }
    }
    if (showAddEventDialog) {
        AddEventDialog(
            initialDate = selectedDate,
            onDismiss = {
                showAddEventDialog = false
            },
            onSave = { newEvent ->
                events.add(newEvent)

                selectedDate = newEvent.date
                displayedMonth = YearMonth.from(newEvent.date)

                showAddEventDialog = false
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MonthCalendar(
    displayedMonth: YearMonth,
    events: List<CalendarEvent>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    selectedDate: LocalDate
) {
    val firstDay = displayedMonth.atDay(1)
    val startOffset = firstDay.dayOfWeek.value - 1
    val daysInMonth = displayedMonth.lengthOfMonth()
    val totalCells = ((startOffset + daysInMonth + 6) / 7) * 7

    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = firstDay.format(monthFormatter),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Previous month"
                        )
                    }

                    IconButton(onClick = onNextMonth) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next month"
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                listOf(
                    "M", "T", "W", "T", "F", "S", "S"
                ).forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            repeat(totalCells / 7) { week ->
                Row(Modifier.fillMaxWidth()) {

                    repeat(7) { dayOfWeek ->

                        val dayNumber =
                            week * 7 + dayOfWeek - startOffset + 1

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNumber in 1..daysInMonth) {

                                val date = displayedMonth.atDay(dayNumber)
                                val isSelected = date == selectedDate
                                val dayStatuses = events
                                    .filter { it.date == date }
                                    .map { it.status }
                                    .filter {
                                        it == EventStatus.CONFIRMED ||
                                                it == EventStatus.PENDING
                                    }
                                    .distinct()

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            onDateSelected(date)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isSelected)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    Color.Transparent,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(Modifier.height(3.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        dayStatuses.forEach { status ->
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(
                                                        color = eventStatusColor(status),
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(event: CalendarEvent) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = event.status.name
                        .lowercase()
                        .replaceFirstChar { it.uppercase() },
                    color = eventStatusColor(event.status),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text("${event.startTime}–${event.endTime}")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(event.location)
            }
        }
    }
}

//TODO change colours to theme
@Composable
fun eventStatusColor(status: EventStatus): Color {
    return when (status) {
        EventStatus.CONFIRMED ->
            MaterialTheme.colorScheme.primary

        EventStatus.PENDING ->
            Color(0xFFE5A000)

        EventStatus.DECLINED ->
            Color(0xFFD64545)

        EventStatus.CANCELLED ->
            Color(0xFF89938D)
    }
}