package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert
    suspend fun insertMatch(match: MatchEntity): Long

    @Query("SELECT * FROM matches WHERE ownerUserId = :userId ORDER BY playedAt DESC")
    fun getMatchesForUser(userId: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id AND ownerUserId = :userId LIMIT 1")
    suspend fun getMatchByIdForUser(id: Long, userId: Long): MatchEntity?

    @Query("""
        SELECT * FROM matches
        WHERE id = :matchId
          AND (
              ownerUserId = :userId
              OR player2Id = CAST(:userId AS TEXT)
          )
        LIMIT 1
    """)
    suspend fun getMatchByIdVisibleToUser(matchId: Long, userId: Long): MatchEntity?

    @Query("DELETE FROM matches WHERE id = :matchId AND ownerUserId = :userId")
    suspend fun deleteMatchByIdForUser(matchId: Long, userId: Long): Int

    @Query("UPDATE matches SET photoUri = :photoUri WHERE id = :matchId AND ownerUserId = :userId")
    suspend fun updateMatchPhotoForUser(matchId: Long, userId: Long, photoUri: String?): Int

    @Query("SELECT * FROM matches WHERE isCompleted = 1 AND ownerUserId = :userId ORDER BY playedAt DESC")
    fun getCompletedMatchesForUser(userId: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE isCompleted = 1 AND ownerUserId IN (:userIds) ORDER BY playedAt DESC")
    fun getCompletedMatchesForUsers(userIds: List<Long>): Flow<List<MatchEntity>>

    @Query("""
        SELECT * FROM matches
        WHERE ownerUserId = :userId
           OR player2Id = CAST(:userId AS TEXT)
        ORDER BY playedAt DESC
    """)
    fun getMatchesVisibleToUser(userId: Long): Flow<List<MatchEntity>>

    @Query("""
    SELECT photoUri FROM matches
    WHERE ownerUserId = :userId
      AND photoUri IS NOT NULL
""")
    suspend fun getOwnedMatchPhotoPaths(
        userId: Long
    ): List<String>

    @Query("""
    DELETE FROM matches
    WHERE ownerUserId = :userId
""")
    suspend fun deleteOwnedMatchesForUser(
        userId: Long
    ): Int

    @Query("""
        UPDATE matches
        SET player2Id = NULL,
            player2Name = 'Deleted account'
        WHERE player2Id = CAST(:userId AS TEXT)
          AND (
              ownerUserId IS NULL
              OR ownerUserId != :userId
          )
    """)
    suspend fun anonymizeOpponentForDeletedUser(userId: Long): Int
}
