package com.example.courtpalapp.feature.friends

import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
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
import com.example.courtpalapp.data.local.FriendActivityEntity
import com.example.courtpalapp.data.local.FriendDisplay
import com.example.courtpalapp.data.local.FriendEntity
import com.example.courtpalapp.data.local.MatchEntity
import com.example.courtpalapp.data.local.SessionManager
import com.example.courtpalapp.feature.calendar.EventStatus
import com.example.courtpalapp.feature.profile.recordEarnedBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

private sealed interface FriendFeedItem {
    val ownerUserId: Long
    val timestamp: Long
    val stableKey: String

    data class MatchPost(
        val match: MatchEntity,
        val friendUserId: Long
    ) : FriendFeedItem {
        override val ownerUserId: Long get() = friendUserId
        override val timestamp: Long get() = match.playedAt
        override val stableKey: String get() = "match_${match.id}"
    }

    data class BadgePost(val badge: FriendActivityEntity) : FriendFeedItem {
        override val ownerUserId: Long get() = requireNotNull(badge.ownerUserId)
        override val timestamp: Long get() = badge.timestamp
        override val stableKey: String get() = "badge_${badge.id}"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FriendsScreen(
    onNavigateToCalendarEvent: (Long) -> Unit = {},
    onNavigateToCalendarDate: (String) -> Unit = {},
    onAcceptedMatch: (Long) -> Unit = {}
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

    val matchDao = remember(database) { database.matchDao() }
    val activityDao = remember(database) { database.friendActivityDao() }

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

    val friendUserIds = remember(friends) { friends.map { it.friendUserId } }
    val friendsById = remember(friends) { friends.associateBy { it.friendUserId } }

    val friendMatches = key(currentUserId, friendUserIds) {
        remember(friendUserIds, matchDao) {
            matchDao.getCompletedMatchesForUsers(friendUserIds)
        }.collectAsState(initial = emptyList()).value
    }

    val earnedBadges = key(currentUserId, friendUserIds) {
        remember(friendUserIds, activityDao) {
            activityDao.getBadgeActivitiesForUsers(friendUserIds)
        }.collectAsState(initial = emptyList()).value
    }

    // Populate previously earned badges, and react to newly saved friend matches.
    LaunchedEffect(currentUserId, friendUserIds, friendMatches) {
        if (currentUserId != null) {
            friendUserIds.forEach { friendId ->
                try {
                    recordEarnedBadges(database, friendId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("CourtPalBadges", "Could not refresh friend badges", error)
                }
            }
        }
    }

    val friendActivities: List<FriendFeedItem> =
        remember(friendMatches, earnedBadges, friendUserIds) {
            val matchPosts = friendMatches.mapNotNull { match ->
                val creatorId = match.ownerUserId
                val opponentId = match.player2Id?.toLongOrNull()
                val activityFriendId = when {
                    creatorId != null && creatorId in friendUserIds ->
                        creatorId
                    opponentId != null && opponentId in friendUserIds ->
                        opponentId
                    else -> null
                }
                activityFriendId?.let { friendId ->
                    FriendFeedItem.MatchPost(
                        match = match,
                        friendUserId = friendId
                    )
                }
            }
            (
                    matchPosts +
                            earnedBadges.map { FriendFeedItem.BadgePost(it) }
                    ).sortedWith(
                    compareByDescending<FriendFeedItem> { it.timestamp }
                        .thenBy { it.stableKey }
                )
        }

    val matchParticipantIds = remember(friendMatches) {
        friendMatches.flatMap { match ->
            listOfNotNull(
                match.ownerUserId,
                match.player2Id?.toLongOrNull()
            )
        }.distinct()
    }

    var matchUsernames by remember {
        mutableStateOf<Map<Long, String>>(emptyMap())
    }

    LaunchedEffect(matchParticipantIds, friends, userDao) {
        val usernames = matchParticipantIds.mapNotNull { userId ->
            userDao.getUserById(userId)?.let { user ->
                userId to user.username
            }
        }.toMap()

        matchUsernames = usernames
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
                                    onAcceptedMatch(proposal.id)
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
                    key = { it.stableKey }
                ) { activity ->
                    friendsById[activity.ownerUserId]?.let { friend ->
                        FriendActivityCard(activity, friend, matchUsernames)
                    }
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

                                        // The friendship is already saved; badge recording
                                        // should not turn a successful add into an error.
                                        try {
                                            recordEarnedBadges(database, ownerId)
                                            recordEarnedBadges(database, targetUser.id)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (badgeError: Exception) {
                                            Log.w("CourtPalBadges", "Could not record friend badge", badgeError)
                                        }

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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun FriendActivityCard(
    post: FriendFeedItem,
    friend: FriendDisplay,
    matchUsernames: Map<Long, String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FriendAvatar(friend.avatarUri)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = formatUsername(friend.username),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = Instant.ofEpochMilli(post.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.UK)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (post) {
                is FriendFeedItem.MatchPost -> {
                    val match = post.match
                    val friendIsCreator = match.ownerUserId == post.friendUserId

                    val friendWon = when (match.winner) {
                        1 -> friendIsCreator
                        2 -> !friendIsCreator
                        else -> null
                    }

                    val creatorName = match.ownerUserId
                        ?.let { matchUsernames[it] }
                        ?.let { formatUsername(it) }
                        ?: if (match.player1Name.equals("You", ignoreCase = true)) {
                            "Unknown player"
                        } else {
                            match.player1Name
                        }

                    val opponentName = match.player2Id
                        ?.toLongOrNull()
                        ?.let { matchUsernames[it] }
                        ?.let { formatUsername(it) }
                        ?: match.player2Name

                    val player1DisplayName =
                        if (friendIsCreator) {
                            formatUsername(friend.username)
                        } else {
                            creatorName
                        }

                    val player2DisplayName =
                        if (!friendIsCreator) {
                            formatUsername(friend.username)
                        } else {
                            opponentName
                        }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsTennis,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Played a tennis match",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = when (friendWon) {
                                true -> "Match won"
                                false -> "Match lost"
                                null -> "Match completed"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text("$player1DisplayName vs $player2DisplayName")
                        Text(
                            text = "Sets: ${formatMatchSets(match)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is FriendFeedItem.BadgePost -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Earned a new badge!", fontWeight = FontWeight.SemiBold)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = badgeTitle(post.badge.badgeId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = post.badge.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun badgeTitle(id: String?): String = when (id) {
    "first_match" -> "First Match"
    "social_butterfly" -> "Social Butterfly"
    "getting_started" -> "Getting Started"
    "court_regular" -> "Court Regular"
    "match_master" -> "Match Master"
    "habit_builder" -> "Habit Builder"
    "consistent_player" -> "Consistent Player"
    "on_fire" -> "On Fire"
    "long_game" -> "Long Game"
    "early_riser" -> "Early Riser"
    "night_owl" -> "Night Owl"
    "organiser" -> "Organiser"
    "true_self" -> "True Self"
    else -> "Achievement"
}

private fun formatMatchSets(match: MatchEntity): String {
    return try {
        val json = JSONArray(match.completedSetsJson)
        (0 until json.length()).mapNotNull { index ->
            json.optJSONObject(index)?.let { set ->
                "${set.optInt("player1Games")}–${set.optInt("player2Games")}" 
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(" | ")
            ?: "${match.player1Sets}–${match.player2Sets}"
    } catch (_: Exception) {
        "${match.player1Sets}–${match.player2Sets}"
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
            text = "Your friends' completed matches and earned badges will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
