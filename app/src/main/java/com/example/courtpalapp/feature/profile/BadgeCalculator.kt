package com.example.courtpalapp.feature.profile

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.courtpalapp.data.local.MatchEntity
import java.time.Instant
import java.time.ZoneId

data class AchievementBadge(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val unlocked: Boolean
)

@RequiresApi(Build.VERSION_CODES.O)
fun calculateBadges(
    matches: List<MatchEntity>,
    stats: MatchStats,
    hasFriends: Boolean = false,
    hasSentCalendarRequest: Boolean = false,
    hasSoloCalendarEvent: Boolean = false
): List<AchievementBadge> {

    val completed = matches.filter { it.isCompleted }
    val zone = ZoneId.systemDefault()

    val hours = completed.map { match ->
        Instant.ofEpochMilli(match.playedAt)
            .atZone(zone)
            .hour
    }

    return listOf(
        AchievementBadge(
            "first_match",
            "First Match",
            "Complete your first tennis match.",
            Icons.Default.SportsTennis,
            stats.totalMatches >= 1
        ),

        AchievementBadge(
            "social_butterfly",
            "Social Butterfly",
            "Make your first friend.",
            Icons.Default.People,
            hasFriends
        ),

        AchievementBadge(
            "getting_started",
            "Getting Started",
            "Complete 5 matches.",
            Icons.Default.Celebration,
            stats.totalMatches >= 5
        ),

        AchievementBadge(
            "court_regular",
            "Court Regular",
            "Complete 10 matches.",
            Icons.Default.MilitaryTech,
            stats.totalMatches >= 10
        ),

        AchievementBadge(
            "match_master",
            "Match Master",
            "Complete 20 matches.",
            Icons.Default.EmojiEvents,
            stats.totalMatches >= 20
        ),

        AchievementBadge(
            "habit_builder",
            "Habit Builder",
            "Play in 2 consecutive weeks.",
            Icons.Default.DateRange,
            stats.longestWeeklyStreak >= 2
        ),

        AchievementBadge(
            "consistent_player",
            "Consistent Player",
            "Play in 4 consecutive weeks.",
            Icons.Default.Sync,
            stats.longestWeeklyStreak >= 4
        ),

        AchievementBadge(
            "on_fire",
            "On Fire",
            "Play in 8 consecutive weeks.",
            Icons.Default.LocalFireDepartment,
            stats.longestWeeklyStreak >= 8
        ),

        AchievementBadge(
            "long_game",
            "Long Game",
            "Record a rally of at least 10 shots.",
            Icons.Default.Timeline,
            completed.any { it.longestRally >= 10 }
        ),

        AchievementBadge(
            "early_riser",
            "Early Riser",
            "Record a match between 2 AM and 9 AM.",
            Icons.Default.WbSunny,
            hours.any { it in 2..8 }
        ),

        AchievementBadge(
            "night_owl",
            "Night Owl",
            "Record a match between 8 PM and 2 AM.",
            Icons.Default.NightsStay,
            hours.any { it !in 2..<20 }
        ),

        AchievementBadge(
            "organiser",
            "Organiser",
            "Send a calendar match request.",
            Icons.Default.Event,
            hasSentCalendarRequest
        ),

        AchievementBadge(
            "true_self",
            "True Self",
            "Create a solo calendar event.",
            Icons.Default.Person,
            hasSoloCalendarEvent
        )
    )
}
