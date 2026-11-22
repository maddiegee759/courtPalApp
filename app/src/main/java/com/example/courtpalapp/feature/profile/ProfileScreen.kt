package com.example.courtpalapp.feature.profile

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.common.formatUsername
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.ui.theme.PrimaryGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentUserId: Long,
    onEditProfile: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    var showFriends by remember { mutableStateOf(false) }

    // Match statistics from Room
    val context = LocalContext.current

    val database = remember(context) { AppDatabase.getDatabase(context) }
    val userDao = remember(database) { database.userDao() }

    val currentUser by remember(userDao, currentUserId) {
        userDao.observeUser(currentUserId)
    }.collectAsState(initial = null)

    val username = currentUser?.username ?: ""

    val avatarPath = currentUser?.avatarUri

    val profileBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = currentUserId,
        key2 = avatarPath
    ) {
        value = null

        value = withContext(Dispatchers.IO) {
            avatarPath?.let { BitmapFactory.decodeFile(it) }
        }
    }

    val matchDao = remember(database) { database.matchDao() }

    val friendDao = remember(database) { database.friendDao() }

    val friends by remember(friendDao, currentUserId) {
        friendDao.observeFriendDisplaysForUser(currentUserId)
    }.collectAsState(initial = emptyList())

    val eventDao = remember(database) { database.calendarEventDao() }

    val calendarEvents by remember(eventDao, currentUserId) {
        eventDao.getEventsForUser(currentUserId)
    }.collectAsState(initial = emptyList())

    val completedMatches by remember(matchDao, currentUserId) {
        matchDao.getCompletedMatchesForUser(currentUserId)
    }.collectAsState(initial = emptyList())

    val stats = calculateMatchStats(completedMatches)

    val weeklyStreak = stats.weeklyStreak
    val totalMatches = stats.totalMatches

    val friendsCount = friends.size

    val badges = remember(completedMatches, stats, friends, calendarEvents) {
        calculateBadges(
            matches = completedMatches,
            stats = stats,
            hasFriends = friends.isNotEmpty(),

            hasSentCalendarRequest = calendarEvents.any { event ->
                event.ownerUserId == currentUserId && !event.friendId.isNullOrBlank()
            },

            hasSoloCalendarEvent = calendarEvents.any { event ->
                event.ownerUserId == currentUserId && event.friendId.isNullOrBlank()
            }
        )
    }

    val unlockedBadges = badges.filter { it.unlocked }
    val lockedBadges = badges.filter { !it.unlocked }

    LaunchedEffect(currentUserId, badges) {
        try {
            recordEarnedBadges(database, currentUserId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("CourtPalBadges", "Could not record earned badges", error)
        }
    }

    val streakColor = when {
        weeklyStreak == 0 ->
            MaterialTheme.colorScheme.onSurface

        stats.playedThisWeek ->
            PrimaryGreen

        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        // Header: Profile left, Settings right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onSettingsClick
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // pfp and username
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap!!.asImageBitmap(),
                        contentDescription = "Profile picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default profile picture",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "@$username",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.clickable {
                    showFriends = true
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.People, contentDescription = null)

                Spacer(Modifier.width(6.dp))

                Text("$friendsCount Friends")

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Profile actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onEditProfile,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Edit Profile")
            }

            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Find me on CourtPal! My username is @$username 🎾"
                        )
                    }

                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            "Share CourtPal Profile"
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Share Profile")
            }
        }

        Spacer(Modifier.height(28.dp))

        HorizontalDivider()

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Stats",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileStatCard(
                value = weeklyStreak.toString(),
                label = "Week Streak",
                icon = Icons.Default.LocalFireDepartment,
                modifier = Modifier.weight(1f),
                contentColor = streakColor
            )

            ProfileStatCard(
                value = totalMatches.toString(),
                label = "Total Matches",
                icon = Icons.Default.EmojiEvents,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(28.dp))


        Text(
            text = "Badges",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // Unlocked achievements
        Text(
            text = "Unlocked (${unlockedBadges.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        if (unlockedBadges.isEmpty()) {
            Text(
                text = "Complete matches to start earning badges!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            BadgeGrid(unlockedBadges)
        }

        Spacer(Modifier.height(24.dp))

        // Locked achievements
        Text(
            text = "Locked (${lockedBadges.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        if (lockedBadges.isNotEmpty()) {
            BadgeGrid(lockedBadges)
        }
    }

    // Friends overlay
    if (showFriends) {
        ModalBottomSheet(
            onDismissRequest = { showFriends = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Friends ($friendsCount)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (friends.isEmpty()) {
                    Text(
                        text = "No friends added yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    friends.forEach { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend.avatarUri)

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text = formatUsername(friend.username),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
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

@Composable
private fun ProfileStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}


@Composable
private fun BadgeGrid(
    badges: List<AchievementBadge>
) {
    badges.chunked(3).forEach { rowBadges ->

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rowBadges.forEach { badge ->
                AchievementBadgeCard(
                    badge = badge,
                    modifier = Modifier.weight(1f)
                )
            }

            repeat(3 - rowBadges.size) {
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}
