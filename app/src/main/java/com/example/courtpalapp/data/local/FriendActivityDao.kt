package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendActivityDao {

    @Query(
        "SELECT * FROM friend_activities ORDER BY timestamp DESC, id DESC"
    )
    fun getAllActivities(): Flow<List<FriendActivityEntity>>

    @Insert
    suspend fun insertActivity(activity: FriendActivityEntity): Long

    @Query("UPDATE friend_activities SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM friend_activities WHERE id = :id")
    suspend fun deleteActivity(id: Long)
}