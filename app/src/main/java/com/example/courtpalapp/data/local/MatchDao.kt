package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert
    suspend fun insertMatch(match: MatchEntity): Long

    @Query("SELECT * FROM matches ORDER BY playedAt DESC")
    fun getAllMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE ownerUserId = :userId ORDER BY playedAt DESC")
    fun getMatchesForUser(userId: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun getMatchById(id: Long): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :id AND ownerUserId = :userId LIMIT 1")
    suspend fun getMatchByIdForUser(id: Long, userId: Long): MatchEntity?

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatchById(matchId: Long)

    @Query("DELETE FROM matches WHERE id = :matchId AND ownerUserId = :userId")
    suspend fun deleteMatchByIdForUser(matchId: Long, userId: Long): Int

    @Query("UPDATE matches SET photoUri = :photoUri WHERE id = :matchId")
    suspend fun updateMatchPhoto(matchId: Long, photoUri: String?)

    @Query("UPDATE matches SET photoUri = :photoUri WHERE id = :matchId AND ownerUserId = :userId")
    suspend fun updateMatchPhotoForUser(matchId: Long, userId: Long, photoUri: String?): Int

    @Query("SELECT * FROM matches WHERE isCompleted = 1 ORDER BY playedAt DESC")
    fun getCompletedMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE isCompleted = 1 AND ownerUserId = :userId ORDER BY playedAt DESC")
    fun getCompletedMatchesForUser(userId: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE isCompleted = 1 AND ownerUserId IN (:userIds) ORDER BY playedAt DESC")
    fun getCompletedMatchesForUsers(userIds: List<Long>): Flow<List<MatchEntity>>
}
