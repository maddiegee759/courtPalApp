package com.example.courtpalapp.feature.profile

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.courtpalapp.data.local.MatchEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class MatchStats(
    val totalMatches: Int = 0,
    val weeklyStreak: Int = 0,
    val longestWeeklyStreak: Int = 0,
    val playedThisWeek: Boolean = false
)

@RequiresApi(Build.VERSION_CODES.O)
fun calculateMatchStats(matches: List<MatchEntity>): MatchStats {

    val completedMatches = matches.filter { it.isCompleted }
    val zone = ZoneId.systemDefault()

    val playedWeeks = completedMatches.map { match ->
        Instant.ofEpochMilli(match.playedAt)
            .atZone(zone)
            .toLocalDate()
            .with(
                TemporalAdjusters.previousOrSame(
                    DayOfWeek.MONDAY
                )
            )
    }.toSet()

    val currentWeek = LocalDate.now(zone)
        .with(
            TemporalAdjusters.previousOrSame(
                DayOfWeek.MONDAY
            )
        )

    var week: LocalDate? = when {
        currentWeek in playedWeeks -> currentWeek
        currentWeek.minusWeeks(1) in playedWeeks ->
            currentWeek.minusWeeks(1)
        else -> null
    }

    var currentStreak = 0

    while (week != null && week in playedWeeks) {
        currentStreak++
        week = week.minusWeeks(1)
    }

    var longestStreak = 0
    var runningStreak = 0
    var previousWeek: LocalDate? = null

    for (playedWeek in playedWeeks.sorted()) {
        runningStreak = if (
            previousWeek != null &&
            previousWeek.plusWeeks(1) == playedWeek
        ) {
            runningStreak + 1
        } else {
            1
        }

        longestStreak = maxOf(
            longestStreak,
            runningStreak
        )

        previousWeek = playedWeek
    }

    return MatchStats(
        totalMatches = completedMatches.size,
        weeklyStreak = currentStreak,
        longestWeeklyStreak = longestStreak,
        playedThisWeek = currentWeek in playedWeeks
    )
}