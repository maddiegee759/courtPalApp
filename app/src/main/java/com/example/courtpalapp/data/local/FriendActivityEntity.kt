package com.example.courtpalapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "friend_activities",
    indices = [Index(value = ["ownerUserId", "badgeId"], unique = true)]
)
data class FriendActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long?,
    val friendName: String,
    val description: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val ownerUserId: Long? = null,
    val badgeId: String? = null
)
