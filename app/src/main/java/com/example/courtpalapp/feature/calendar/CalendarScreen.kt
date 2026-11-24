package com.example.courtpalapp.feature.calendar

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.api.calendar.CalendarAuthManager
import com.example.courtpalapp.api.calendar.GoogleCalendarManager
import com.example.courtpalapp.common.formatDate
import com.example.courtpalapp.common.formatDateTime
import com.example.courtpalapp.common.formatUsername
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.CalendarEventEntity
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.data.local.toCalendarEvent
import com.example.courtpalapp.data.local.toEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
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

// Legacy version-5 events stored a typed name in both fields.
// They must never be treated as verified linked CourtPal accounts.
private fun CalendarEventEntity.isLinkedCourtPalFriend(): Boolean =
    !friendId.isNullOrBlank() && !friendName.isNullOrBlank() && friendId != friendName

private fun CalendarEventEntity.displayFriendName(): String? =
    friendName?.takeIf { it.isNotBlank() }
        ?: friendId?.takeIf { it.isNotBlank() }

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
    selectedEventId: Long? = null,
    requestedDate: String? = null,
    autoSyncAccepted: Boolean = false
) {

    val today = remember { LocalDate.now() }

    var displayedMonth by remember {
        mutableStateOf(YearMonth.from(today))
    }

    var selectedDate by remember {
        mutableStateOf(today)
    }

    LaunchedEffect(requestedDate) {
        if (requestedDate != null) {
            val targetDate = LocalDate.parse(requestedDate)
            selectedDate = targetDate
            displayedMonth = YearMonth.from(targetDate)
        }
    }

    // room
    val context = LocalContext.current

    val sessionManager = remember(context) {
        SessionManager(context)
    }

    val currentUserId by sessionManager.currentUserId.collectAsState()

    val database = remember(context) {
        AppDatabase.getDatabase(context)
    }

    val eventDao = remember(database) {
        database.calendarEventDao()
    }

    val friendDao = remember(database) {
        database.friendDao()
    }

    val userDao = remember(database) {
        database.userDao()
    }

    val friendsFlow = remember(currentUserId, friendDao) {
        currentUserId?.let { userId ->
            friendDao.observeFriendDisplaysForUser(userId)
        }
    }

    val friends = if (friendsFlow != null) {
        key(currentUserId) {
            friendsFlow.collectAsState(initial = emptyList()).value
        }
    } else {
        emptyList()
    }

    val coroutineScope = rememberCoroutineScope()

    val eventsFlow = remember(currentUserId, eventDao) {
        currentUserId?.let { userId ->
            eventDao.getEventsForUser(userId)
        }
    }

    val savedEvents = if (eventsFlow != null) {
        key(currentUserId) {
            eventsFlow.collectAsState(initial = emptyList()).value
        }
    } else {
        emptyList()
    }

    val ownerUsernames = remember(savedEvents) {
        mutableStateMapOf<Long, String>()
    }

    LaunchedEffect(savedEvents, userDao) {
        savedEvents.mapNotNull { it.ownerUserId }
            .distinct()
            .forEach { ownerId ->
                userDao.getUserById(ownerId)?.let { user ->
                    ownerUsernames[ownerId] = user.username
                }
            }
    }

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
                if (!autoSyncAccepted) {
                    navigatedEvent = target
                }
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
                        val local = eventDao.getEventById(action.roomEventId)
                            ?: throw IllegalStateException("Event no longer exists")

                        val userId = currentUserId
                            ?: throw IllegalStateException("No active user")

                        if (
                            local.isLinkedCourtPalFriend() &&
                            local.ownerUserId != userId
                        ) {
                            throw IllegalStateException(
                                "Only the event creator can delete this match from Google Calendar"
                            )
                        }

                        googleCalendarManager.deleteEvent(
                            accessToken = accessToken,
                            googleEventId = requireNotNull(action.googleEventId)
                        )

                        if (
                            local.isLinkedCourtPalFriend() &&
                            local.status != EventStatus.CANCELLED.name
                        ) {
                            eventDao.updateEvent(
                                local.copy(
                                    status = EventStatus.CANCELLED.name,
                                    googleEventId = null
                                )
                            )
                        } else {
                            eventDao.deleteEvent(local)
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
            } catch (e: Exception) {
                null
            }
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

    var handledAcceptedSyncId by rememberSaveable(selectedEventId) {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(
        autoSyncAccepted,
        selectedEventId,
        savedEvents
    ) {
        if (!autoSyncAccepted || selectedEventId == null) {
            return@LaunchedEffect
        }

        if (handledAcceptedSyncId == selectedEventId) {
            return@LaunchedEffect
        }

        val acceptedEvent = savedEvents.firstOrNull {
            it.id == selectedEventId
        } ?: return@LaunchedEffect

        if (acceptedEvent.status != EventStatus.CONFIRMED.name) {
            return@LaunchedEffect
        }

        // Only create the event if it has not already been synced.
        if (acceptedEvent.googleEventId == null) {

            handledAcceptedSyncId = selectedEventId

            requestGoogleSync(
                PendingGoogleAction(
                    action = GoogleCalendarAction.CREATE,
                    roomEventId = acceptedEvent.id,
                    event = acceptedEvent.toCalendarEvent()
                )
            )
        } else {
            handledAcceptedSyncId = selectedEventId
        }
    }

    var showAddEventDialog by remember {
        mutableStateOf(false)
    }

    var eventToEdit by remember { mutableStateOf<CalendarEventEntity?>(null) }
    var amendmentToConfirm by remember {
        mutableStateOf<Pair<CalendarEventEntity, CalendarEventEntity>?>(
            null
        )
    }
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
                        EventCard(
                            event = entity.toCalendarEvent(),
                            friendName = entity.displayParticipantName(
                                currentUserId = currentUserId,
                                ownerUsername = entity.ownerUserId?.let { ownerUsernames[it] }
                            )
                        )

                        if (entity.proposedTitle != null) {

                            val isIncomingAmendment =
                                currentUserId != null &&
                                        entity.proposedByUserId != null &&
                                        entity.proposedByUserId != currentUserId &&
                                        (
                                                entity.ownerUserId == currentUserId ||
                                                        entity.friendId == currentUserId.toString()
                                                )

                            Text(
                                text = if (isIncomingAmendment) {
                                    "Amendment awaiting your approval"
                                } else {
                                    "Amendment awaiting friend's approval"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "Current: ${
                                    formatDateTime(entity.date, entity.startTime)
                                }",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Proposed: ${
                                    formatDateTime(
                                        entity.proposedDate ?: entity.date,
                                        entity.proposedStartTime ?: entity.startTime
                                    )
                                }",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Proposed location: ${
                                    entity.proposedLocation ?: entity.location
                                }",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Original match remains confirmed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isIncomingAmendment) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val userId = currentUserId ?: return@launch

                                                try {
                                                    val updated = eventDao.acceptAmendment(
                                                        eventId = entity.id,
                                                        currentUserId = userId
                                                    )

                                                    if (updated == 1) {
                                                        snackbarHostState.showSnackbar(
                                                            "Amendment accepted"
                                                        )
                                                    } else {
                                                        snackbarHostState.showSnackbar(
                                                            "Amendment can no longer be accepted"
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(
                                                        "Could not accept amendment"
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Accept")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val userId = currentUserId ?: return@launch

                                                try {
                                                    val updated = eventDao.declineAmendment(
                                                        eventId = entity.id,
                                                        currentUserId = userId
                                                    )

                                                    snackbarHostState.showSnackbar(
                                                        if (updated == 1) {
                                                            "Amendment declined"
                                                        } else {
                                                            "Amendment can no longer be declined"
                                                        }
                                                    )
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(
                                                        "Could not decline amendment"
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Decline")
                                    }
                                }
                            }
                        }
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
                        } else if (
                            (
                                    entity.status != EventStatus.PENDING.name ||
                                            entity.ownerUserId == currentUserId
                                    ) &&
                            !(
                                    entity.proposedTitle != null &&
                                            entity.proposedByUserId != null &&
                                            entity.proposedByUserId != currentUserId &&
                                            (
                                                    entity.ownerUserId == currentUserId ||
                                                            entity.friendId == currentUserId?.toString()
                                                    )
                                    )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { eventToEdit = entity },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Edit")
                                }

                                if (
                                    !entity.isLinkedCourtPalFriend() ||
                                    entity.ownerUserId == currentUserId
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            eventToDelete = entity
                                            removeFromGoogle = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (
                            entity.status == EventStatus.PENDING.name &&
                            entity.isLinkedCourtPalFriend() &&
                            entity.friendId == currentUserId?.toString() &&
                            entity.ownerUserId != currentUserId
                        ) {
                            Text(
                                text = if (entity.ownerUserId == currentUserId) {
                                    "Proposed to ${
                                        entity.displayParticipantName(
                                            currentUserId,
                                            entity.ownerUserId?.let { ownerUsernames[it] }
                                        )
                                    }"
                                } else {
                                    "Proposal from ${
                                        entity.displayParticipantName(
                                            currentUserId,
                                            entity.ownerUserId?.let { ownerUsernames[it] }
                                        )
                                    }"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val userId = currentUserId ?: return@launch

                                                val updated = eventDao.acceptProposal(
                                                    eventId = entity.id,
                                                    currentUserId = userId
                                                )

                                                if (updated == 1) {
                                                    val acceptedEvent =
                                                        eventDao.getEventById(entity.id)

                                                    if (
                                                        acceptedEvent != null &&
                                                        acceptedEvent.googleEventId == null
                                                    ) {
                                                        requestGoogleSync(
                                                            PendingGoogleAction(
                                                                action = GoogleCalendarAction.CREATE,
                                                                roomEventId = acceptedEvent.id,
                                                                event = acceptedEvent.toCalendarEvent()
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    snackbarHostState.showSnackbar(
                                                        "This proposal can no longer be accepted"
                                                    )
                                                }
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
                                            try {
                                                val userId = currentUserId ?: return@launch

                                                val updated = eventDao.declineProposal(
                                                    eventId = entity.id,
                                                    currentUserId = userId
                                                )

                                                if (updated == 0) {
                                                    snackbarHostState.showSnackbar(
                                                        "This proposal can no longer be declined"
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(
                                                    "Could not decline the match"
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text("Decline")
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    if (showAddEventDialog) {
        AddEventDialog(
            initialDate = selectedDate,
            courtPalFriends = friends.map { friend ->
                CalendarFriendOption(
                    userId = friend.friendUserId.toString(),
                    username = friend.username
                )
            },
            onDismiss = {
                showAddEventDialog = false
            },

            onSave = { newEvent, syncToGoogle, friendName, friendId ->

                coroutineScope.launch {
                    try {
                        val ownerId = currentUserId ?: return@launch

                        val roomEventId = eventDao.insertEvent(
                            newEvent.toEntity(
                                friendId = friendId,
                                friendName = friendName
                            ).copy(
                                ownerUserId = ownerId
                            )
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
                            if (existing.isLinkedCourtPalFriend() &&
                                existing.status == EventStatus.CONFIRMED.name
                            ) {
                                eventToEdit = null
                                if (existing.proposedTitle != null) {
                                    snackbarHostState.showSnackbar("An amendment is already awaiting approval")
                                } else if (existing.title == edited.title && existing.date == edited.date &&
                                    existing.startTime == edited.startTime && existing.endTime == edited.endTime &&
                                    existing.location == edited.location
                                ) {
                                    snackbarHostState.showSnackbar("No changes to propose")
                                } else {
                                    amendmentToConfirm = existing to edited
                                }
                                return@launch
                            }
                            if (
                                existing.isLinkedCourtPalFriend() &&
                                existing.status == EventStatus.PENDING.name
                            ) {
                                val userId = currentUserId ?: return@launch

                                val updated = eventDao.updatePendingProposal(
                                    eventId = existing.id,
                                    currentUserId = userId,
                                    title = edited.title,
                                    date = edited.date,
                                    startTime = edited.startTime,
                                    endTime = edited.endTime,
                                    location = edited.location
                                )

                                if (updated != 1) {
                                    snackbarHostState.showSnackbar(
                                        "Only the sender can edit a pending proposal"
                                    )
                                    return@launch
                                }
                            } else {
                                eventDao.updateEvent(edited)
                            }
                            selectedDate = LocalDate.parse(edited.date)
                            displayedMonth = YearMonth.from(selectedDate)
                            eventToEdit = null

                            if (existing.googleEventId != null) {
                                requestGoogleSync(
                                    PendingGoogleAction(
                                        action = GoogleCalendarAction.UPDATE,
                                        roomEventId = existing.id,
                                        event = edited.toCalendarEvent(),
                                        googleEventId = existing.googleEventId
                                    )
                                )
                            } else {
                                snackbarHostState.showSnackbar("Event updated in courtPal")
                            }
                            if (existing.isLinkedCourtPalFriend()) {
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

    amendmentToConfirm?.let { (original, proposed) ->
        AlertDialog(
            onDismissRequest = { amendmentToConfirm = null },
            title = { Text("Send amended proposal?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your confirmed match will not change until the amendment is accepted.")
                    Text(
                        "Current: ${original.title} · ${
                            formatDate(original.date)
                        } · ${original.startTime}–${original.endTime} · ${original.location}"
                    )

                    Text(
                        "Proposed: ${proposed.title} · ${
                            formatDate(proposed.date)
                        } · ${proposed.startTime}–${proposed.endTime} · ${proposed.location}"
                    )
                    Text("The other participant can accept or decline your proposed changes.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    amendmentToConfirm = null
                    coroutineScope.launch {
                        try {
                            val userId = currentUserId ?: return@launch

                            val saved = eventDao.submitAmendment(
                                eventId = original.id,
                                currentUserId = userId,
                                title = proposed.title,
                                date = proposed.date,
                                startTime = proposed.startTime,
                                endTime = proposed.endTime,
                                location = proposed.location
                            )
                            snackbarHostState.showSnackbar(
                                if (saved == 1)
                                    "Amendment sent for approval"
                                else "Could not propose changes; refresh the event"
                            )
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Could not save amended proposal")
                        }
                    }
                }) { Text("Send proposal") }
            },
            dismissButton = {
                TextButton(onClick = { amendmentToConfirm = null }) { Text("Keep original") }
            }
        )
    }

    eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = {
                Text(
                    when {
                        event.status == EventStatus.CANCELLED.name -> "Delete cancelled match permanently?"
                        event.isLinkedCourtPalFriend() -> "Cancel match?"
                        else -> "Delete event?"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            event.status == EventStatus.CANCELLED.name ->
                                "Permanently remove this cancelled match from your courtPal calendar? This cannot be undone."

                            event.isLinkedCourtPalFriend() ->
                                "Cancel this match in courtPal?"

                            else -> "Delete this event from courtPal?"
                        }
                    )
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
                    if (
                        event.googleEventId != null &&
                        removeFromGoogle &&
                        (
                                !event.isLinkedCourtPalFriend() ||
                                        event.ownerUserId == currentUserId
                                )
                    ) {
                        requestGoogleSync(
                            PendingGoogleAction(
                                action = GoogleCalendarAction.DELETE,
                                roomEventId = event.id,
                                googleEventId = event.googleEventId
                            )
                        )
                        eventToDelete = null
                    } else {
                        coroutineScope.launch {
                            try {
                                if (event.status == EventStatus.CANCELLED.name) {
                                    eventDao.deleteEvent(event)

                                } else if (event.isLinkedCourtPalFriend()) {
                                    val userId = currentUserId ?: return@launch

                                    val cancelled = eventDao.cancelOwnedEvent(
                                        eventId = event.id,
                                        currentUserId = userId
                                    )

                                    if (cancelled == 0) {
                                        snackbarHostState.showSnackbar(
                                            "Only the event creator can cancel this match"
                                        )
                                        return@launch
                                    }

                                } else {
                                    eventDao.deleteEvent(event)
                                }
                                eventToDelete = null
                                snackbarHostState.showSnackbar(
                                    if (event.status == EventStatus.CANCELLED.name)
                                        "Cancelled match permanently deleted from CourtPal"
                                    else if (event.isLinkedCourtPalFriend())
                                        "Match cancelled"
                                    else
                                        "Event deleted from CourtPal"
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
                    target.displayParticipantName(
                        currentUserId = currentUserId,
                        ownerUsername = target.ownerUserId?.let { ownerUsernames[it] })
                        ?.let { Text("Playing with: $it") }
                    if (target.googleEventId == null) {
                        Text("Would you like to add this match to Google Calendar?")
                    } else {
                        Text("Would you like to update the existing Google Calendar event with these match details?")
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
                    TextButton(onClick = {
                        navigatedEvent = null
                        requestGoogleSync(
                            PendingGoogleAction(
                                action = GoogleCalendarAction.UPDATE,
                                roomEventId = target.id,
                                event = target.toCalendarEvent(),
                                googleEventId = target.googleEventId
                            )
                        )
                    }) { Text("Yes, update event") }
                }
            },
            dismissButton = {
                TextButton(onClick = { navigatedEvent = null }) { Text("No thanks") }
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
fun EventCard(event: CalendarEvent, friendName: String? = null) {

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

            if (!friendName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = "Friend event",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = friendName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

            if (event.location.isNotBlank()) {
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

private fun CalendarEventEntity.displayParticipantName(
    currentUserId: Long?,
    ownerUsername: String?
): String? {
    val name = displayFriendName() ?: return null

    if (!isLinkedCourtPalFriend()) {
        return name
    }

    return if (currentUserId != null &&
        ownerUserId != null &&
        currentUserId != ownerUserId
    ) {
        ownerUsername?.let(::formatUsername)
    } else {
        formatUsername(name)
    }
}