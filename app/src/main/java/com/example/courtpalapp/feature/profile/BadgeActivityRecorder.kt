package com.example.courtpalapp.feature.profile

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.FriendActivityEntity
import com.example.courtpalapp.data.local.MatchEntity
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@RequiresApi(Build.VERSION_CODES.O)
suspend fun recordEarnedBadges(database: AppDatabase, userId: Long) {
    val user = database.userDao().getUserById(userId) ?: return
    val matches = database.matchDao().getCompletedMatchesForUser(userId).first()
    val firstFriendship = database.friendDao().getFirstFriendshipTime(userId)
    val ownedEvents = database.calendarEventDao()
        .getEventsForUser(userId).first()
        .filter { it.ownerUserId == userId }

    val badges = calculateBadges(
        matches = matches,
        stats = calculateMatchStats(matches),
        hasFriends = firstFriendship != null,
        hasSentCalendarRequest = ownedEvents.any { !it.friendId.isNullOrBlank() },
        hasSoloCalendarEvent = ownedEvents.any { it.friendId.isNullOrBlank() }
    )

    val detectedAt = System.currentTimeMillis()
    badges.filter { it.unlocked }.forEach { badge ->
        val calendarEarnedAt = when (badge.id) {
            "organiser" -> ownedEvents
                .filter { !it.friendId.isNullOrBlank() }
                .mapNotNull { it.createdAt.takeIf { time -> time > 0 } }
                .minOrNull()
            "true_self" -> ownedEvents
                .filter { it.friendId.isNullOrBlank() }
                .mapNotNull { it.createdAt.takeIf { time -> time > 0 } }
                .minOrNull()
            else -> null
        }
        // The unique index on (ownerUserId, badgeId) makes retries safe.
        database.friendActivityDao().insertActivity(
            FriendActivityEntity(
                eventId = null,
                friendName = user.username,
                description = badge.description,
                type = "BADGE_EARNED",
                timestamp = earnedAt(badge.id, matches, firstFriendship)
                    ?: calendarEarnedAt ?: detectedAt,
                ownerUserId = userId,
                badgeId = badge.id
            )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun earnedAt(
    badgeId: String,
    matches: List<MatchEntity>,
    firstFriendship: Long?
): Long? {
    val completed = matches.filter { it.isCompleted }.sortedBy { it.playedAt }
    val zone = ZoneId.systemDefault()

    return when (badgeId) {
        "first_match" -> completed.firstOrNull()?.playedAt
        "getting_started" -> completed.getOrNull(4)?.playedAt
        "court_regular" -> completed.getOrNull(9)?.playedAt
        "match_master" -> completed.getOrNull(19)?.playedAt
        "social_butterfly" -> firstFriendship
        "long_game" -> completed.firstOrNull { it.longestRally >= 10 }?.playedAt
        "early_riser" -> completed.firstOrNull {
            Instant.ofEpochMilli(it.playedAt).atZone(zone).hour in 2..8
        }?.playedAt
        "night_owl" -> completed.firstOrNull {
            Instant.ofEpochMilli(it.playedAt).atZone(zone).hour !in 2..<20
        }?.playedAt
        "habit_builder" -> streakMilestoneTime(completed, 2)
        "consistent_player" -> streakMilestoneTime(completed, 4)
        "on_fire" -> streakMilestoneTime(completed, 8)
        // Calendar-based badges use CalendarEventEntity.createdAt separately.
        else -> null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun streakMilestoneTime(matches: List<MatchEntity>, weeks: Int): Long? {
    val zone = ZoneId.systemDefault()
    val firstMatchInWeek = matches.groupBy { match ->
        Instant.ofEpochMilli(match.playedAt).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }.toSortedMap().mapValues { (_, weekMatches) ->
        weekMatches.minOf { it.playedAt }
    }

    var previous: LocalDate? = null
    var consecutive = 0
    for ((week, playedAt) in firstMatchInWeek) {
        consecutive = if (previous?.plusWeeks(1) == week) consecutive + 1 else 1
        if (consecutive >= weeks) return playedAt
        previous = week
    }
    return null
}