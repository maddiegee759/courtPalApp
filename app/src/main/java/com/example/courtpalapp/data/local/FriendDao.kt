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
        SELECT * FROM friends
        WHERE ownerUserId = :userId
        ORDER BY username COLLATE NOCASE ASC
    """)
    fun getFriendsForUser(userId: Long): Flow<List<FriendEntity>>

    @Query("""
        SELECT * FROM friends
        WHERE id = :id LIMIT 1
    """)
    suspend fun getFriendById(id: Long): FriendEntity?

    @Query("""
        SELECT COUNT(DISTINCT CASE
            WHEN ownerUserId = :userId THEN friendUserId
            ELSE ownerUserId END)
        FROM friends
        WHERE ownerUserId = :userId OR friendUserId = :userId
    """)
    fun getFriendCountForUser(userId: Long): Flow<Int>

    @Query("""
        SELECT MIN(addedAt) FROM friends
        WHERE ownerUserId = :userId OR friendUserId = :userId
    """)
    suspend fun getFirstFriendshipTime(userId: Long): Long?

    @Query("""
        SELECT * FROM friends
        ORDER BY username COLLATE NOCASE ASC
    """)
    fun getAllFriends(): Flow<List<FriendEntity>>

    @Query("SELECT COUNT(*) FROM friends")
    fun getFriendCount(): Flow<Int>

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteFriendById(id: Long)
}
