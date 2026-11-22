
package com.example.courtpalapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "friends",
    indices = [
        Index(
            value = ["ownerUserId", "friendUserId"],
            unique = true
        )
    ]
)
data class FriendEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val ownerUserId: Long,
    val friendUserId: Long,

    val username: String,
    val avatarUri: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
