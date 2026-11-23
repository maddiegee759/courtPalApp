package com.example.courtpalapp.data.local

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import com.example.courtpalapp.domain.auth.PasswordHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class AccountDeletionRepository(context: Context) {

    private val appContext = context.applicationContext

    private val database = AppDatabase.getDatabase(appContext)

    private val sessionManager = SessionManager(appContext)

    private val preferencesRepository =
        UserPreferencesRepository(appContext)

    private data class AccountPhotos(
        val matchPhotos: List<String>,
        val profilePhoto: String?
    )

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun deleteAccount(
        userId: Long,
        password: String
    ) {
        require(userId > 0L)

        check(sessionManager.currentUserId.first() == userId) {
            "Please log in to delete your account"
        }

        val user = database.userDao().getUserById(userId)
            ?: error("Account not found")

        val verifier = user.passwordVerifier
            ?: error("Set an account password before deletion")

        val passwordCorrect = withContext(Dispatchers.Default) {
            PasswordHasher.verify(password, verifier)
        }

        require(passwordCorrect) {
            "Incorrect password"
        }

        check(sessionManager.currentUserId.first() == userId) {
            "Account changed while deleting"
        }

        var databaseDeletionCompleted = false

        try {
            val photos = database.withTransaction {

                val latest = database.userDao()
                    .getUserById(userId)
                    ?: error("Account not found")

                check(latest.passwordVerifier == verifier) {
                    "Account credentials changed"
                }

                val matchPhotos = database.matchDao()
                    .getOwnedMatchPhotoPaths(userId)

                val profilePhoto = latest.avatarUri

                // Preserve unfinished matches tracked by other accounts,
// but remove references to the account being deleted.
                val activeMatchDao = database.activeMatchDao()
                val deletedUserId = userId.toString()

                activeMatchDao.getActiveMatchesOwnedByOthers(userId)
                    .forEach { activeMatch ->

                        val settings = ActiveMatchJson.settingsFromJson(
                            activeMatch.settingsJson
                        )

                        val deletedIsPlayer1 = settings.player1Id == deletedUserId
                        val deletedIsPlayer2 = settings.player2Id == deletedUserId

                        if (deletedIsPlayer1 || deletedIsPlayer2) {
                            val updatedSettings = settings.copy(
                                player1Id = if (deletedIsPlayer1) {
                                    null
                                } else {
                                    settings.player1Id
                                },

                                player1Name = if (deletedIsPlayer1) {
                                    "Deleted account"
                                } else {
                                    settings.player1Name
                                },

                                player2Id = if (deletedIsPlayer2) {
                                    null
                                } else {
                                    settings.player2Id
                                },

                                player2Name = if (deletedIsPlayer2) {
                                    "Deleted account"
                                } else {
                                    settings.player2Name
                                }
                            )

                            val updated = activeMatchDao.updateAnonymizedActiveMatch(
                                matchId = activeMatch.id,
                                ownerUserId = requireNotNull(activeMatch.ownerUserId),
                                settingsJson = ActiveMatchJson.settingsToJson(
                                    updatedSettings
                                ),
                                player1Name = updatedSettings.player1Name,
                                player2Name = updatedSettings.player2Name
                            )

                            check(updated == 1) {
                                "Could not anonymize unfinished match"
                            }
                        }
                    }

                // Remove calendar references before deleting calendar events.
                activeMatchDao.detachActiveMatchesFromDeletedEvents(userId)
                // Delete activity before its calendar events,
                // because activity records may reference event IDs.
                database.friendActivityDao()
                    .deleteActivitiesForAccount(userId)

                database.calendarEventDao()
                    .deleteEventsForUser(userId)

                database.friendDao()
                    .deleteFriendshipsForUser(userId)

                database.activeMatchDao()
                    .deleteActiveMatchesForUser(userId)

                // Keep other users' matches, but remove
                // references to the deleted opponent.
                database.matchDao()
                    .anonymizeOpponentForDeletedUser(userId)

                // Remove matches tracked by this account.
                // Shared matches disappear from both histories.
                database.matchDao()
                    .deleteOwnedMatchesForUser(userId)

                database.trainingSessionDao()
                    .deleteAllForUser(userId)

                val deleted = database.userDao()
                    .deleteUserById(userId)

                check(deleted == 1) {
                    "Could not delete account"
                }

                AccountPhotos(
                    matchPhotos = matchPhotos,
                    profilePhoto = profilePhoto
                )
            }

            databaseDeletionCompleted = true

            // Files and DataStore are outside Room's transaction.
            // Perform best-effort cleanup after the DB commit.
            try {
                preferencesRepository.clearForUser(userId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(
                    "CourtPalDeletion",
                    "Could not clear account preferences",
                    e
                )
            }

            withContext(NonCancellable + Dispatchers.IO) {

                photos.matchPhotos.forEach { path ->
                    try {
                        deleteAppOwnedFile(
                            folderName = "match_photos",
                            path = path
                        )
                    } catch (e: Exception) {
                        Log.w(
                            "CourtPalDeletion",
                            "Could not remove match photo",
                            e
                        )
                    }
                }

                photos.profilePhoto?.let { path ->
                    try {
                        deleteAppOwnedFile(
                            folderName = "profile_photos",
                            path = path
                        )
                    } catch (e: Exception) {
                        Log.w(
                            "CourtPalDeletion",
                            "Could not remove profile photo",
                            e
                        )
                    }
                }
            }

        } finally {
            // Once the database deletion commits, never leave
            // the removed account signed in.
            if (databaseDeletionCompleted) {
                sessionManager.logout()
            }
        }
    }

    private fun deleteAppOwnedFile(
        folderName: String,
        path: String
    ) {
        val folder = File(
            appContext.filesDir,
            folderName
        ).canonicalFile

        val file = File(path).canonicalFile

        // Only delete files inside CourtPal's private folder.
        if (file.parentFile == folder) {
            file.delete()
        }
    }
}