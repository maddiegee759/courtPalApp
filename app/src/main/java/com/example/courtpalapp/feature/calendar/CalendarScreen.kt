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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.toCalendarEvent
import com.example.courtpalapp.data.local.toEntity
import kotlinx.coroutines.launch
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.courtpalapp.api.calendar.CalendarAuthManager
import com.example.courtpalapp.api.calendar.GoogleCalendarManager
import com.example.courtpalapp.data.local.CalendarEventEntity
import java.time.LocalTime
import java.time.ZoneId

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

private enum class GoogleCalendarAction { CREATE, UPDATE, DELETE }

private data class PendingGoogleAction(
    val action: GoogleCalendarAction,
    val roomEventId: Long,
    val event: CalendarEvent? = null,
    val googleEventId: String? = null
)

private fun Context.findActivity(): Activity {
    var current: Context = this

    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }

    error("An Activity is required for Google authorisation")
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarScreen(
    selectedEventId: Long? = null
) {

    val today = remember { LocalDate.now() }

    var displayedMonth by remember {
        mutableStateOf(YearMonth.from(today))
    }

    var selectedDate by remember {
        mutableStateOf(today)
    }

    var acceptedEventToSync by remember {
        mutableStateOf<CalendarEventEntity?>(null)
    }

    // room
    val context = LocalContext.current
    val database = remember(context) {
        AppDatabase.getDatabase(context)
    }
    val eventDao = remember(database) {
        database.calendarEventDao()
    }

    val coroutineScope = rememberCoroutineScope()

    val savedEvents by eventDao.getAllEvents()
        .collectAsState(initial = emptyList())

    val events = savedEvents.map { it.toCalendarEvent() }

    // Open the accepted match when navigating here from Friends.
    var navigatedEvent by remember(selectedEventId) {
        mutableStateOf<CalendarEventEntity?>(null)
    }
    var handledNavigationId by rememberSaveable(selectedEventId) {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(selectedEventId, savedEvents) {
        if (selectedEventId != null && handledNavigationId != selectedEventId) {
            val target = savedEvents.firstOrNull { it.id == selectedEventId }
            if (target != null) {
                val targetDate = LocalDate.parse(target.date)
                selectedDate = targetDate
                displayedMonth = YearMonth.from(targetDate)
                navigatedEvent = target
                handledNavigationId = selectedEventId
            }
        }
    }


    val activity = LocalContext.current.findActivity()

    val authManager = remember(activity) {
        CalendarAuthManager(activity)
    }

    val googleCalendarManager = remember {
        GoogleCalendarManager()
    }

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    var pendingGoogleAction by remember { mutableStateOf<PendingGoogleAction?>(null) }
    var failedGoogleAction by remember { mutableStateOf<PendingGoogleAction?>(null) }
    var googleBusy by remember { mutableStateOf(false) }

    fun uploadToGoogle(accessToken: String) {
        val action = pendingGoogleAction ?: return
        if (googleBusy) return
        googleBusy = true

        coroutineScope.launch {
            try {
                val event = action.event
                val zone = ZoneId.systemDefault()
                val startDateTime = event?.let {
                    it.date.atTime(LocalTime.parse(it.startTime))
                        .atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
                val endDateTime = event?.let {
                    it.date.atTime(LocalTime.parse(it.endTime))
                        .atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }

                when (action.action) {
                    GoogleCalendarAction.CREATE -> {
                        // Room ID check prevents normal retries from creating a second event.
                        val local = eventDao.getEventById(action.roomEventId)
                        if (local != null && local.googleEventId == null) {
                            val source = requireNotNull(event)
                            val googleId = googleCalendarManager.createEvent(
                                accessToken = accessToken,
                                title = source.title,
                                location = source.location,
                                description = "Created with courtPal",
                                startDateTime = requireNotNull(startDateTime),
                                endDateTime = requireNotNull(endDateTime)
                            )
                            eventDao.updateGoogleEventId(action.roomEventId, googleId)
                        }
                    }
                    GoogleCalendarAction.UPDATE -> {
                        val source = requireNotNull(event)
                        googleCalendarManager.updateEvent(
                            accessToken = accessToken,
                            googleEventId = requireNotNull(action.googleEventId),
                            title = source.title,
                            location = source.location,
                            description = "Updated with courtPal",
                            startDateTime = requireNotNull(startDateTime),
                            endDateTime = requireNotNull(endDateTime)
                        )
                    }
                    GoogleCalendarAction.DELETE -> {
                        googleCalendarManager.deleteEvent(
                            accessToken = accessToken,
                            googleEventId = requireNotNull(action.googleEventId)
                        )
                        // Only remove the local event after Google confirms deletion.
                        eventDao.getEventById(action.roomEventId)?.let { local ->
                            if (local.friendId != null && local.status != EventStatus.CANCELLED.name) {
                                eventDao.updateEvent(local.copy(
                                    status = EventStatus.CANCELLED.name,
                                    googleEventId = null
                                ))
                            } else {
                                eventDao.deleteEvent(local)
                            }
                        }
                    }
                }
                failedGoogleAction = null
                snackbarHostState.showSnackbar("Google Calendar ${action.action.name.lowercase()} successful")
            } catch (e: Exception) {
                failedGoogleAction = action
                snackbarHostState.showSnackbar("Google Calendar operation failed. Tap retry.")
            } finally {
                pendingGoogleAction = null
                googleBusy = false
            }
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val token = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                authManager.getAccessToken(authManager.getResultFromIntent(result.data!!))
            } catch (e: Exception) { null }
        } else null

        if (token != null) {
            uploadToGoogle(token)
        } else {
            failedGoogleAction = pendingGoogleAction
            pendingGoogleAction = null
        }
    }

    fun requestGoogleSync(action: PendingGoogleAction) {
        if (pendingGoogleAction != null || googleBusy) return
        pendingGoogleAction = action
        authManager.requestAuthorization(
            onSuccess = { result ->
                if (result.hasResolution()) {
                    try {
                        val request = IntentSenderRequest.Builder(
                            result.pendingIntent!!.intentSender
                        ).build()
                        authorizationLauncher.launch(request)
                    } catch (e: Exception) {
                        failedGoogleAction = action
                        pendingGoogleAction = null
                    }
                } else {
                    val token = authManager.getAccessToken(result)
                    if (token != null) uploadToGoogle(token)
                    else {
                        failedGoogleAction = action
                        pendingGoogleAction = null
                    }
                }
            },
            onError = {
                failedGoogleAction = action
                pendingGoogleAction = null
            }
        )
    }

    var showAddEventDialog by remember {
        mutableStateOf(false)
    }

    var eventToEdit by remember { mutableStateOf<CalendarEventEntity?>(null) }
    var eventToDelete by remember { mutableStateOf<CalendarEventEntity?>(null) }
    var removeFromGoogle by remember { mutableStateOf(true) }

    val selectedEvents = savedEvents.filter {
        it.date == selectedDate.toString()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
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


            if (failedGoogleAction != null) {
                item {
                    OutlinedButton(
                        onClick = {
                            failedGoogleAction?.let { requestGoogleSync(it) }
                        },
                        enabled = pendingGoogleAction == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Google Calendar sync")
                    }
                }
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


                items(
                    items = selectedEvents,
                    key = { it.id }
                ) { entity ->

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EventCard(entity.toCalendarEvent())

                        if (entity.status == EventStatus.CANCELLED.name) {
                            OutlinedButton(
                                onClick = {
                                    eventToDelete = entity
                                    removeFromGoogle = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Delete permanently", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { eventToEdit = entity },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Edit") }
                                OutlinedButton(
                                    onClick = {
                                        eventToDelete = entity
                                        removeFromGoogle = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (
                            entity.status == EventStatus.PENDING.name &&
                            entity.friendId != null
                        ) {
                            Text(
                                text = "Proposed to ${entity.friendId}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                eventDao.updateEventStatus(
                                                    entity.id,
                                                    EventStatus.CONFIRMED.name
                                                )

                                                acceptedEventToSync = entity.copy(
                                                    status = EventStatus.CONFIRMED.name
                                                )
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(
                                                    "Could not confirm the match"
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text("Accept")
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            eventDao.updateEventStatus(
                                                entity.id,
                                                EventStatus.DECLINED.name
                                            )
                                        }
                                    }
                                ) {
                                    Text("Decline")
                                }
                            }

                            Text(
                                text = "Demo: simulate the friend's response",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

            onSave = { newEvent, syncToGoogle, friendId ->

                coroutineScope.launch {
                    try {
                        val roomEventId = eventDao.insertEvent(
                            newEvent.toEntity(friendId = friendId)
                        )

                        selectedDate = newEvent.date
                        displayedMonth = YearMonth.from(newEvent.date)
                        showAddEventDialog = false

                        if (syncToGoogle) {
                            requestGoogleSync(
                                PendingGoogleAction(
                                    action = GoogleCalendarAction.CREATE,
                                    roomEventId = roomEventId,
                                    event = newEvent
                                )
                            )
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            "Could not save event"
                        )
                    }
                }
            }
        )
    }

    eventToEdit?.let { existing ->
        key(existing.id) {
            EditCalendarEventDialog(
                existingEvent = existing,
                onDismiss = { eventToEdit = null },
                onSave = { edited ->
                    coroutineScope.launch {
                        try {
                            eventDao.updateEvent(edited)
                            selectedDate = LocalDate.parse(edited.date)
                            displayedMonth = YearMonth.from(selectedDate)
                            eventToEdit = null

                            if (existing.googleEventId != null) {
                                requestGoogleSync(PendingGoogleAction(
                                    action = GoogleCalendarAction.UPDATE,
                                    roomEventId = existing.id,
                                    event = edited.toCalendarEvent(),
                                    googleEventId = existing.googleEventId
                                ))
                            } else {
                                snackbarHostState.showSnackbar("Event updated in courtPal")
                            }
                            if (existing.friendId != null) {
                                snackbarHostState.showSnackbar(
                                    "Demo only: friend has NOT been notified of the change"
                                )
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Could not update event")
                        }
                    }
                }
            )
        }
    }

    eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text(when {
                event.status == EventStatus.CANCELLED.name -> "Delete cancelled match permanently?"
                event.friendId != null -> "Cancel match?"
                else -> "Delete event?"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when {
                        event.status == EventStatus.CANCELLED.name ->
                            "Permanently remove this cancelled match from your courtPal calendar? This cannot be undone."
                        event.friendId != null ->
                            "Cancel this match in courtPal? This demo does not notify the friend."
                        else -> "Delete this event from courtPal?"
                    })
                    if (event.googleEventId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = removeFromGoogle,
                                onCheckedChange = { removeFromGoogle = it }
                            )
                            Text("Also remove from Google Calendar")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // A Google deletion must finish before the Room record is discarded.
                    if (event.googleEventId != null && removeFromGoogle) {
                        requestGoogleSync(PendingGoogleAction(
                            action = GoogleCalendarAction.DELETE,
                            roomEventId = event.id,
                            googleEventId = event.googleEventId
                        ))
                        eventToDelete = null
                    } else {
                        coroutineScope.launch {
                            try {
                                if (event.friendId != null && event.status != EventStatus.CANCELLED.name) {
                                    eventDao.updateEventStatus(
                                        event.id, EventStatus.CANCELLED.name
                                    )
                                } else {
                                    eventDao.deleteEvent(event)
                                }
                                eventToDelete = null
                                snackbarHostState.showSnackbar(
                                    if (event.status == EventStatus.CANCELLED.name)
                                        "Cancelled match permanently deleted from courtPal"
                                    else if (event.friendId != null)
                                        "Match cancelled locally; no friend notification sent"
                                    else "Event deleted from courtPal"
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Could not delete event")
                            }
                        }
                    }
                }) { Text("Confirm", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) { Text("Keep event") }
            }
        )
    }

    navigatedEvent?.let { target ->
        AlertDialog(
            onDismissRequest = { navigatedEvent = null },
            title = { Text("Match confirmed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.title, style = MaterialTheme.typography.titleMedium)
                    Text("Date: ${target.date}")
                    Text("Time: ${target.startTime}–${target.endTime}")
                    Text("Location: ${target.location}")
                    target.friendId?.let { Text("Playing with: $it") }
                    if (target.googleEventId == null) {
                        Text("Would you like to add this match to Google Calendar?")
                    } else {
                        Text("Already linked to Google Calendar")
                    }
                }
            },
            confirmButton = {
                if (target.googleEventId == null) {
                    TextButton(onClick = {
                        navigatedEvent = null
                        requestGoogleSync(
                            PendingGoogleAction(
                                action = GoogleCalendarAction.CREATE,
                                roomEventId = target.id,
                                event = target.toCalendarEvent()
                            )
                        )
                    }) { Text("Yes, add event") }
                } else {
                    TextButton(onClick = { navigatedEvent = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (target.googleEventId == null) {
                    TextButton(onClick = { navigatedEvent = null }) {
                        Text("No thanks")
                    }
                }
            }
        )
    }

    acceptedEventToSync?.let { acceptedEvent ->

        AlertDialog(
            onDismissRequest = {
                acceptedEventToSync = null
            },
            title = {
                Text("Add to Google Calendar?")
            },
            text = {
                Text(
                    "Your tennis match has been confirmed. " +
                            "Would you like to add it to your Google Calendar?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        acceptedEventToSync = null

                        requestGoogleSync(
                            PendingGoogleAction(
                                action = GoogleCalendarAction.CREATE,
                                roomEventId = acceptedEvent.id,
                                event = acceptedEvent.toCalendarEvent()
                            )
                        )
                    }
                ) {
                    Text("Yes, add event")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        acceptedEventToSync = null
                    }
                ) {
                    Text("No thanks")
                }
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


/** Editing uses the original Room ID and preserves status, friendId and googleEventId. */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun EditCalendarEventDialog(
    existingEvent: CalendarEventEntity,
    onDismiss: () -> Unit,
    onSave: (CalendarEventEntity) -> Unit
) {
    val context = LocalContext.current
    var title by remember(existingEvent.id) { mutableStateOf(existingEvent.title) }
    var location by remember(existingEvent.id) { mutableStateOf(existingEvent.location) }
    var date by remember(existingEvent.id) { mutableStateOf(LocalDate.parse(existingEvent.date)) }
    var start by remember(existingEvent.id) { mutableStateOf(LocalTime.parse(existingEvent.startTime)) }
    var end by remember(existingEvent.id) { mutableStateOf(LocalTime.parse(existingEvent.endTime)) }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tennis Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event title") },
                    singleLine = true
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            date = LocalDate.of(year, month + 1, day)
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }
                ) { Text("Date: $date") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                start = LocalTime.of(hour, minute)
                            }, start.hour, start.minute, true).show()
                        }
                    ) { Text(start.format(formatter)) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                end = LocalTime.of(hour, minute)
                            }, end.hour, end.minute, true).show()
                        }
                    ) { Text(end.format(formatter)) }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true
                )
                existingEvent.friendId?.let {
                    Text("Match with $it (friend cannot be changed)",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && end.isAfter(start),
                onClick = {
                    onSave(existingEvent.copy(
                        title = title.trim(),
                        date = date.toString(),
                        startTime = start.format(formatter),
                        endTime = end.format(formatter),
                        location = location.trim()
                    ))
                }
            ) { Text("Save changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}