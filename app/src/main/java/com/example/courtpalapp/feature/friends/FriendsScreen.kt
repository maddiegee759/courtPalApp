package com.example.courtpalapp.feature.friends

import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.common.formatDateTime
import com.example.courtpalapp.common.formatUsername
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.FriendDisplay
import com.example.courtpalapp.data.local.FriendEntity
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.feature.calendar.EventStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FriendMatchProposal(
    val id: Long,
    val friendName: String,
    val date: String,
    val time: String,
    val location: String,
    val ownerUserId: Long?,
    val friendId: String?
)

enum class FriendActivityType {
    MATCH_COMPLETED,
    MATCH_CANCELLED,
    MATCH_UPDATED,
    BADGE_EARNED,
    STREAK_MILESTONE
}

data class FriendActivity(
    val id: Long,
    val friendName: String,
    val description: String,
    val date: String,
    val type: FriendActivityType,
    val photoUri: String? = null
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FriendsScreen(
    onNavigateToCalendarEvent: (Long) -> Unit = {},
    onNavigateToCalendarDate: (String) -> Unit = {}
) {
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

    var showAddFriendDialog by remember {
        mutableStateOf(false)
    }

    var addFriendError by remember {
        mutableStateOf<String?>(null)
    }

    var friendToRemove by remember {
        mutableStateOf<FriendDisplay?>(null)
    }

    val coroutineScope = rememberCoroutineScope()

    val eventsFlow = remember(currentUserId, eventDao) {
        currentUserId?.let { eventDao.getEventsForUser(it) }
    }

    val savedEvents = if (eventsFlow != null) {
        key(currentUserId) {
            eventsFlow.collectAsState(initial = emptyList()).value
        }
    } else {
        emptyList()
    }

    val ownerIds = remember(savedEvents) {
        savedEvents.mapNotNull { it.ownerUserId }.distinct()
    }

    var ownerUsernames by remember {
        mutableStateOf<Map<Long, String>>(emptyMap())
    }

    LaunchedEffect(ownerIds, userDao) {
        ownerUsernames = ownerIds.mapNotNull { ownerId ->
            userDao.getUserById(ownerId)?.let { user ->
                ownerId to user.username
            }
        }.toMap()
    }

    val pendingProposals = savedEvents
        .filter {
            it.status == EventStatus.PENDING.name &&
                    !it.friendId.isNullOrBlank() &&
                    !it.friendName.isNullOrBlank() &&
                    it.friendId != it.friendName &&
                    currentUserId != null &&
                    it.friendId == currentUserId.toString() &&
                    it.ownerUserId != currentUserId
        }
        .map { event ->
            FriendMatchProposal(
                id = event.id,
                friendName = event.ownerUserId
                    ?.let { ownerUsernames[it] }
                    ?.let { "@$it" }
                    ?: "Unknown user",
                date = event.date,
                time = "${event.startTime}–${event.endTime}",
                location = event.location,
                ownerUserId = event.ownerUserId,
                friendId = event.friendId
            )
        }

    val amendments = savedEvents.filter {
        it.proposedTitle != null && it.status == EventStatus.CONFIRMED.name &&
                !it.friendId.isNullOrBlank() && !it.friendName.isNullOrBlank() && it.friendId != it.friendName
    }

    val cancelledMatches = savedEvents.filter { event ->
        event.status == EventStatus.CANCELLED.name &&
                currentUserId != null &&
                event.friendId == currentUserId.toString() &&
                event.ownerUserId != currentUserId
    }

    val friendActivities = emptyList<FriendActivity>()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Friends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { showAddFriendDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add friend"
                        )
                    }
                }
            }

            if (friends.isEmpty()) {
                item {
                    Text(
                        text = "No friends added yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = friends,
                    key = { "friend_${it.id}" }
                ) { friend ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend.avatarUri)

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text = formatUsername(friend.username),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium
                            )

                            IconButton(
                                onClick = { friendToRemove = friend }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove ${friend.username}"
                                )
                            }
                        }
                    }
                }
            }

            // Only show proposals if there are pending matches.
            if (pendingProposals.isNotEmpty()) {

                item {
                    Text(
                        text = "Match Proposals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(
                    items = pendingProposals,
                    key = { "proposal_${it.id}" }
                ) { proposal ->

                    MatchProposalCard(
                        proposal = proposal,
                        canRespond = currentUserId != null &&
                                proposal.friendId == currentUserId.toString() &&
                                proposal.ownerUserId != currentUserId,

                        onAccept = {
                            coroutineScope.launch {
                                val userId = currentUserId ?: return@launch

                                val updated = eventDao.acceptProposal(
                                    eventId = proposal.id,
                                    currentUserId = userId
                                )

                                if (updated == 1) {
                                    onNavigateToCalendarEvent(proposal.id)
                                }
                            }
                        },

                        onDecline = {
                            coroutineScope.launch {
                                val userId = currentUserId ?: return@launch

                                eventDao.declineProposal(
                                    eventId = proposal.id,
                                    currentUserId = userId
                                )
                            }
                        }
                    )
                }
            }

            if (amendments.isNotEmpty()) {
                item {
                    Text("Amended Match Proposals",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                items(amendments, key = { "amendment_${it.id}" }) { event ->

                    val isIncomingAmendment =
                        currentUserId != null &&
                                event.proposedByUserId != null &&
                                event.proposedByUserId != currentUserId &&
                                (
                                        event.ownerUserId == currentUserId ||
                                                event.friendId == currentUserId.toString()
                                        )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isIncomingAmendment) {
                                    "Amendment awaiting your approval"
                                } else {
                                    "Amendment awaiting friend's approval"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Current:\n" +
                                        "${event.title}\n" +
                                        "${formatDateTime(event.date, event.startTime)}–${event.endTime}\n" +
                                        event.location,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "Proposed:\n" +
                                        "${event.proposedTitle ?: event.title}\n" +
                                        "${formatDateTime(
                                            event.proposedDate ?: event.date,
                                            event.proposedStartTime ?: event.startTime
                                        )}–${event.proposedEndTime ?: event.endTime}\n" +
                                        (event.proposedLocation ?: event.location),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isIncomingAmendment) {
                                    "Review the proposed changes above."
                                } else {
                                    "Waiting for the other participant to respond."
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (isIncomingAmendment) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val userId = currentUserId ?: return@launch

                                                if (eventDao.acceptAmendment(event.id, userId) == 1) {
                                                    onNavigateToCalendarEvent(event.id)
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

                                                eventDao.declineAmendment(event.id, userId)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Decline")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (cancelledMatches.isNotEmpty()) {

                item {
                    Text(
                        text = "Cancelled Matches",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(
                    cancelledMatches,
                    key = { "cancelled_${it.id}" }
                ) { event ->

                    val senderUsername = event.ownerUserId
                        ?.let { ownerUsernames[it] }
                        ?.let { "@$it" }
                        ?: "Unknown user"

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            Text(
                                text = "Match cancelled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "$senderUsername cancelled your match",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = formatDateTime(
                                    event.date,
                                    event.startTime
                                ) + "–${event.endTime}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = event.location,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedButton(
                                onClick = {
                                    onNavigateToCalendarDate(event.date)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View in Calendar")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Friends Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (friendActivities.isEmpty()) {

                item {
                    EmptyFriendsActivity()
                }

            } else {

                items(
                    items = friendActivities,
                    key = { "activity_${it.id}" }
                ) { activity ->

                    FriendActivityCard(activity)
                }
            }
        }
    }

    if (showAddFriendDialog) {
        var username by remember {
            mutableStateOf("")
        }

        AlertDialog(
            onDismissRequest = {
                showAddFriendDialog = false
            },
            title = {
                Text("Add Friend")
            },
            text = {
                Column() {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            addFriendError = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        isError = addFriendError != null
                    )
                    addFriendError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = username.isNotBlank(),
                    onClick = {
                        coroutineScope.launch {
                            val ownerId = currentUserId
                            val enteredUsername = username.trim()

                            if (ownerId == null) {
                                addFriendError = "Please log in first"
                                return@launch
                            }

                            val targetUser = userDao.getUserByUsername(enteredUsername)

                            when {
                                targetUser == null -> {
                                    addFriendError = "No CourtPal account found"
                                }

                                targetUser.id == ownerId -> {
                                    addFriendError = "You cannot add yourself"
                                }

                                friends.any { it.friendUserId == targetUser.id } -> {
                                    addFriendError = "This user is already your friend"
                                }

                                else -> {
                                    try {
                                        friendDao.insertFriend(
                                            FriendEntity(
                                                ownerUserId = ownerId,
                                                friendUserId = targetUser.id,
                                                username = targetUser.username,
                                                avatarUri = targetUser.avatarUri
                                            )
                                        )

                                        showAddFriendDialog = false
                                        addFriendError = null
                                    } catch (e: Exception) {
                                        addFriendError = "Could not add friend"
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddFriendDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    friendToRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = {
                friendToRemove = null
            },
            title = {
                Text("Remove Friend?")
            },
            text = {
                Text("Remove ${formatUsername(friend.username)} from your friends?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            currentUserId?.let { userId ->
                                friendDao.deleteFriendship(userId, friend.friendUserId)
                            }
                            friendToRemove = null
                        }
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        friendToRemove = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FriendAvatar(avatarPath: String?) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, avatarPath) {
        value = withContext(Dispatchers.IO) {
            avatarPath?.let { BitmapFactory.decodeFile(it) }
        }
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Friend profile picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Default profile picture",
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MatchProposalCard(
    proposal: FriendMatchProposal,
    canRespond: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = "${proposal.friendName} invited you to play!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val formattedDate = remember(proposal.date) {
                LocalDate.parse(proposal.date).format(
                    DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
                )
            }

            Text(
                text = "$formattedDate ${proposal.time}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (proposal.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(proposal.location)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accept")
                }

                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
private fun FriendActivityCard(
    activity: FriendActivity
) {
    val icon = when (activity.type) {
        FriendActivityType.MATCH_COMPLETED ->
            Icons.Default.SportsTennis

        FriendActivityType.MATCH_CANCELLED ->
            Icons.Default.EventBusy

        FriendActivityType.MATCH_UPDATED ->
            Icons.Default.EditCalendar

        FriendActivityType.BADGE_EARNED ->
            Icons.Default.EmojiEvents

        FriendActivityType.STREAK_MILESTONE ->
            Icons.Default.LocalFireDepartment
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = activity.friendName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = activity.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // TODO: Display photo when photo support is added.
            }
        }
    }
}

@Composable
private fun EmptyFriendsActivity() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "No friends activity yet",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Your friends' matches, badges and streaks will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
