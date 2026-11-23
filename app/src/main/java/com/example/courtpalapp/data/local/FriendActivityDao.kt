package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivity(activity: FriendActivityEntity): Long

    @Query("""
        SELECT * FROM friend_activities
        WHERE type = 'BADGE_EARNED' AND ownerUserId IN (:userIds)
        ORDER BY timestamp DESC, id DESC
    """)
    fun getBadgeActivitiesForUsers(userIds: List<Long>): Flow<List<FriendActivityEntity>>

    @Query("""
        DELETE FROM friend_activities
        WHERE ownerUserId = :userId
           OR eventId IN (
               SELECT id FROM calendar_events
               WHERE ownerUserId = :userId
                  OR friendId = CAST(:userId AS TEXT)
           )
    """)
    suspend fun deleteActivitiesForAccount(userId: Long): Int
}
