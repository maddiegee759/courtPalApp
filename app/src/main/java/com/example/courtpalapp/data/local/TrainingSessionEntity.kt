package com.example.courtpalapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "training_sessions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerUserId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ownerUserId"])
    ]
)
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val ownerUserId: Long,

    val activityType: String,

    val durationMinutes: Int,

    val notes: String = "",

    val occurredAt: Long = System.currentTimeMillis()
)