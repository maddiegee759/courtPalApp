package com.example.courtpalapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val player1Name: String,
    val player2Name: String,
    val player2Id: String?,

    val winner: Int,

    val player1Sets: Int,
    val player2Sets: Int,

    val player1Games: Int,
    val player2Games: Int,

    val player1TotalPoints: Int,
    val player2TotalPoints: Int,

    val longestRally: Int,
    val averageRally: Double,

    val bestOfSets: Int,
    val gamesPerSet: Int,
    val completedSetsJson: String = "[]",

    val scoringMode: String = "ADVANTAGE",
    val tieBreakEnabled: Boolean = true,
    val tieBreakPoints: Int = 7,
    val trackRallies: Boolean = false,
    val matchMode: String = "LIVE",

    val playedAt: Long = System.currentTimeMillis(),
    val photoUri: String? = null
)
