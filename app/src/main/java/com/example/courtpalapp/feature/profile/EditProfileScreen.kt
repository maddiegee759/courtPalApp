package com.example.courtpalapp.feature.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.AppDatabase
import com.example.courtpalapp.data.local.copyProfilePhoto
import com.example.courtpalapp.data.local.deleteProfilePhoto
import com.example.courtpalapp.data.local.saveProfileBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditProfileScreen(
    currentUserId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val userDao = remember(context) {
        AppDatabase.getDatabase(context).userDao()
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val user by remember(userDao, currentUserId) {
        userDao.observeUser(currentUserId)
    }.collectAsState(initial = null)

    var username by remember(currentUserId) { mutableStateOf("") }
    var photoPath by remember(currentUserId) { mutableStateOf<String?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var initialised by remember(currentUserId) { mutableStateOf(false) }

    // The original profile photo remains untouched until Save.
    var newPhotoPath by remember(currentUserId) {
        mutableStateOf<String?>(null)
    }

    // Temporary photo deleted when navigating away
    DisposableEffect(currentUserId) {
        onDispose {
            newPhotoPath?.let { path ->
                deleteProfilePhoto(context, path)
            }
        }
    }

    LaunchedEffect(user) {
        if (!initialised && user != null) {
            username = user!!.username
            photoPath = user!!.avatarUri
            initialised = true
        }
    }

    LaunchedEffect(photoPath) {
        photoBitmap = withContext(Dispatchers.IO) {
            photoPath?.let { BitmapFactory.decodeFile(it) }
        }
    }

    fun selectPhoto(source: suspend () -> String?) {
        scope.launch {
            try {
                val path = withContext(Dispatchers.IO) { source() }
                    ?: return@launch

                // Discard an earlier unsaved selection.
                newPhotoPath?.let { previous ->
                    withContext(Dispatchers.IO) {
                        deleteProfilePhoto(context, previous)
                    }
                }

                newPhotoPath = path
                photoPath = path
            } catch (e: Exception) {
                snackbar.showSnackbar("Could not load photo")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectPhoto { copyProfilePhoto(context, uri) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectPhoto { saveProfileBitmap(context, bitmap) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("← Back to Profile")
            }

            Text(
                "Edit Profile",
                style = MaterialTheme.typography.headlineMedium
            )

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Profile picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default profile picture",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Take photo")
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Choose photo")
                }
            }

            if (photoPath != null) {
                TextButton(
                    onClick = {
                        photoPath = null
                    }
                ) {
                    Text("Remove photo")
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                prefix = { Text("@") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        val trimmedUsername = username.trim()

                        if (trimmedUsername.isBlank()) {
                            snackbar.showSnackbar("Username cannot be empty")
                            return@launch
                        }

                        isSaving = true

                        try {
                            val existing = userDao.getUserByUsername(
                                trimmedUsername
                            )

                            if (existing != null &&
                                existing.id != currentUserId
                            ) {
                                snackbar.showSnackbar(
                                    "Username already taken"
                                )
                                return@launch
                            }

                            withContext(Dispatchers.IO) {
                                userDao.updateUsername(
                                    userId = currentUserId,
                                    username = trimmedUsername
                                )

                                userDao.updateProfilePhoto(
                                    userId = currentUserId,
                                    avatarUri = photoPath
                                )
                            }

                            val oldPhoto = user?.avatarUri

                            if (oldPhoto != null &&
                                oldPhoto != photoPath
                            ) {
                                withContext(Dispatchers.IO) {
                                    deleteProfilePhoto(context, oldPhoto)
                                }
                            }

                            newPhotoPath = null
                            onBack()

                        } catch (e: Exception) {
                            snackbar.showSnackbar(
                                "Could not save profile changes"
                            )
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && initialised,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSaving) "Saving..." else "Save Changes")
            }
        }
    }
}
