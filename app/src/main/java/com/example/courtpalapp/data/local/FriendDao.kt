package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class FriendDisplay(
    val id: Long,
    val friendUserId: Long,
    val username: String,
    val avatarUri: String?
)

@Dao
interface FriendDao {

    @Query("""
        SELECT MIN(f.id) AS id,
               u.id AS friendUserId,
               u.username AS username,
               u.avatarUri AS avatarUri
        FROM friends AS f
        INNER JOIN users AS u
            ON u.id = CASE
                WHEN f.ownerUserId = :userId THEN f.friendUserId
                ELSE f.ownerUserId
            END
        WHERE f.ownerUserId = :userId OR f.friendUserId = :userId
        GROUP BY u.id
        ORDER BY u.username COLLATE NOCASE ASC
    """)
    fun observeFriendDisplaysForUser(userId: Long): Flow<List<FriendDisplay>>

    @Query("""
        DELETE FROM friends
        WHERE (ownerUserId = :userId AND friendUserId = :otherUserId)
           OR (ownerUserId = :otherUserId AND friendUserId = :userId)
    """)
    suspend fun deleteFriendship(userId: Long, otherUserId: Long)

    @Insert
    suspend fun insertFriend(friend: FriendEntity): Long

    @Query("""
        SELECT MIN(addedAt) FROM friends
        WHERE ownerUserId = :userId OR friendUserId = :userId
    """)
    suspend fun getFirstFriendshipTime(userId: Long): Long?

}
