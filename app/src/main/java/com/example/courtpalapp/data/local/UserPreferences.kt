package com.example.courtpalapp.data.local

data class UserPreferences(
    val scoringMode: String = "LIVE",
    val matchFormat: String = "3",
    val rallyTracking: Boolean = false,
    val calendarSync: String = "ASK",
    val theme: String = "SYSTEM"
)