package com.example.courtpalapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_matches")
data class ActiveMatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val calendarEventId: Long? = null,

    val player1Name: String,
    val player2Name: String,

    val settingsJson: String,
    val scoreJson: String,
    val historyJson: String,

    val rallyShots: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
