package com.example.courtpalapp.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.UserDao
import com.example.courtpalapp.data.local.UserEntity
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    userDao: UserDao,
    onLogin: (Long) -> Unit
) {
    val users by remember(userDao) {
        userDao.getAllUsers()
    }.collectAsState(initial = emptyList())

    var username by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to CourtPal",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Choose an account or create a new one.")

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val newUsername = username.trim()

                if (newUsername.isBlank()) {
                    errorMessage = "Enter a username"
                } else {
                    scope.launch {
                        isCreating = true

                        try {
                            val existing = userDao.getUserByUsername(newUsername)

                            if (existing != null) {
                                errorMessage = "Username already exists"
                            } else {
                                val userId = userDao.insertUser(
                                    UserEntity(
                                        username = newUsername,
                                        displayName = newUsername
                                    )
                                )

                                onLogin(userId)
                            }
                        } catch (e: Exception) {
                            errorMessage = "Could not create account"
                        } finally {
                            isCreating = false
                        }
                    }
                }
            },
            enabled = !isCreating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create account")
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider()

        Text(
            text = "Existing accounts",
            style = MaterialTheme.typography.titleMedium
        )

        if (users.isEmpty()) {
            Text("No accounts created yet.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    OutlinedButton(
                        onClick = { onLogin(user.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("@${user.username}")
                    }
                }
            }
        }
    }
}