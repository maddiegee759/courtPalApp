package com.example.courtpalapp.feature.friends

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.feature.calendar.EventStatus
import kotlinx.coroutines.launch

data class FriendMatchProposal(
    val id: Long,
    val friendName: String,
    val date: String,
    val time: String,
    val location: String
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

@Composable
fun FriendsScreen(
    onNavigateToCalendarEvent: (Long) -> Unit = {}
) {
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

    val pendingProposals = savedEvents
        .filter {
            it.status == EventStatus.PENDING.name &&
                    it.friendId != null
        }
        .map { event ->
            FriendMatchProposal(
                id = event.id,
                friendName = event.friendId ?: "",
                date = event.date,
                time = "${event.startTime}–${event.endTime}",
                location = event.location
            )
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

                        onAccept = {
                            coroutineScope.launch {
                                eventDao.updateEventStatus(
                                    proposal.id,
                                    EventStatus.CONFIRMED.name
                                )

                                onNavigateToCalendarEvent(proposal.id)
                            }
                        },

                        onDecline = {
                            coroutineScope.launch {
                                eventDao.updateEventStatus(
                                    proposal.id,
                                    EventStatus.DECLINED.name
                                )
                            }
                        }
                    )
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
}

@Composable
private fun MatchProposalCard(
    proposal: FriendMatchProposal,
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

            Text(
                text = "${proposal.date} · ${proposal.time}",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(proposal.location)
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

