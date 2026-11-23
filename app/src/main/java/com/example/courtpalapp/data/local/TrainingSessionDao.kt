package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingSessionDao {

    @Insert
    suspend fun insertSession(
        session: TrainingSessionEntity
    ): Long

    @Query("""
        SELECT * FROM training_sessions
        WHERE ownerUserId = :userId
        ORDER BY occurredAt DESC, id DESC
    """)
    fun observeSessionsForUser(
        userId: Long
    ): Flow<List<TrainingSessionEntity>>

    @Query("""
        DELETE FROM training_sessions
        WHERE id = :sessionId
          AND ownerUserId = :userId
    """)
    suspend fun deleteSessionForUser(
        sessionId: Long,
        userId: Long
    ): Int

    @Query("""
        SELECT COALESCE(SUM(durationMinutes), 0)
        FROM training_sessions
        WHERE ownerUserId = :userId
    """)
    fun observeTotalTrainingMinutes(
        userId: Long
    ): Flow<Int>
}