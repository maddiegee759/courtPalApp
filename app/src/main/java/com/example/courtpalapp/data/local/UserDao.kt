package com.example.courtpalapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT * FROM users ORDER BY username COLLATE NOCASE ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUser(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("UPDATE users SET avatarUri = :avatarUri WHERE id = :userId")
    suspend fun updateProfilePhoto(userId: Long, avatarUri: String?)

    @Query("UPDATE users SET username = :username, displayName = :username WHERE id = :userId")
    suspend fun updateUsername(userId: Long, username: String)

    @Query("""
        UPDATE users
        SET passwordVerifier = :verifier
        WHERE id = :userId
          AND passwordVerifier IS NULL
    """)
    suspend fun setInitialPassword(userId: Long, verifier: String): Int

    @Query("""
        UPDATE users
        SET passwordVerifier = :newVerifier
        WHERE id = :userId
          AND passwordVerifier = :oldVerifier
    """)
    suspend fun changePasswordVerifier(userId: Long, oldVerifier: String, newVerifier: String): Int

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Long): Int
}