package com.example.courtpalapp.feature.auth

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.UserDao
import com.example.courtpalapp.data.local.UserEntity
import com.example.courtpalapp.domain.auth.PasswordHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LoginScreen(
    userDao: UserDao,
    onLogin: (Long) -> Unit
) {
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }

    var createUsername by remember { mutableStateOf("") }
    var createPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    var isBusy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // LOGIN

        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = loginUsername,
            onValueChange = {
                loginUsername = it
                loginError = null
            },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )

        CourtPalPasswordField(
            value = loginPassword,
            onValueChange = {
                loginPassword = it
                loginError = null
            },
            label = "Password",
            enabled = !isBusy
        )

        Button(
            onClick = {
                val enteredUsername = loginUsername.trim()
                val enteredPassword = loginPassword

                if (enteredUsername.isBlank() || enteredPassword.isBlank()) {
                    loginError = "Enter your username and password"
                } else {
                    scope.launch {
                        isBusy = true
                        loginError = null

                        try {
                            // Support usernames entered with or without @.
                            val user = userDao.getUserByUsername(
                                enteredUsername
                            ) ?: if (enteredUsername.startsWith("@")) {
                                userDao.getUserByUsername(
                                    enteredUsername.removePrefix("@")
                                )
                            } else {
                                userDao.getUserByUsername(
                                    "@$enteredUsername"
                                )
                            }

                            val storedVerifier = user?.passwordVerifier

                            if (user == null || storedVerifier == null) {
                                loginError = "Incorrect username or password"
                            } else {

                                val passwordCorrect = withContext(
                                    Dispatchers.Default
                                ) {
                                    PasswordHasher.verify(
                                        enteredPassword,
                                        storedVerifier
                                    )
                                }

                                if (!passwordCorrect) {
                                    loginError = "Incorrect username or password"
                                } else {

                                    // Preserve the automatic password-hash
                                    // upgrade introduced previously.
                                    if (PasswordHasher.needsRehash(
                                            storedVerifier
                                        )
                                    ) {
                                        val newVerifier = withContext(
                                            Dispatchers.Default
                                        ) {
                                            PasswordHasher.createVerifier(
                                                enteredPassword
                                            )
                                        }

                                        userDao.changePasswordVerifier(
                                            user.id,
                                            storedVerifier,
                                            newVerifier
                                        )
                                    }

                                    loginPassword = ""
                                    onLogin(user.id)
                                }
                            }

                        } catch (cancelled: CancellationException) {
                            throw cancelled

                        } catch (error: Exception) {
                            loginError = "Could not log in. Please try again."

                        } finally {
                            loginPassword = ""
                            isBusy = false
                        }
                    }
                }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBusy) "Please wait..." else "Log in")
        }

        loginError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(12.dp))

        // CREATE ACCOUNT

        Text(
            text = "Create account",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = createUsername,
            onValueChange = {
                createUsername = it
                createError = null
            },
            label = { Text("Username") },
            singleLine = true,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        )

        CourtPalPasswordField(
            value = createPassword,
            onValueChange = {
                createPassword = it
                createError = null
            },
            label = "Password",
            enabled = !isBusy
        )

        CourtPalPasswordField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                createError = null
            },
            label = "Confirm password",
            enabled = !isBusy
        )

        Button(
            onClick = {
                // Store new usernames without the @ prefix.
                val newUsername = createUsername
                    .trim()
                    .removePrefix("@")

                val newPassword = createPassword

                when {
                    newUsername.isBlank() -> {
                        createError = "Enter a username"
                    }

                    newUsername.any { it.isWhitespace() } -> {
                        createError = "Username cannot contain spaces"
                    }

                    newPassword.length !in 8..20 -> {
                        createError =
                            "Password must be between 8 and 20 characters"
                    }

                    newPassword != confirmPassword -> {
                        createError = "Passwords do not match"
                    }

                    else -> {
                        scope.launch {
                            isBusy = true
                            createError = null

                            try {
                                // Also check for legacy usernames stored
                                // with a leading @.
                                val existingUser =
                                    userDao.getUserByUsername(newUsername)
                                        ?: userDao.getUserByUsername(
                                            "@$newUsername"
                                        )

                                if (existingUser != null) {
                                    createError = "Username already exists"
                                } else {

                                    val verifier = withContext(
                                        Dispatchers.Default
                                    ) {
                                        PasswordHasher.createVerifier(
                                            newPassword
                                        )
                                    }

                                    val userId = userDao.insertUser(
                                        UserEntity(
                                            username = newUsername,
                                            displayName = newUsername,
                                            passwordVerifier = verifier
                                        )
                                    )

                                    createPassword = ""
                                    confirmPassword = ""

                                    onLogin(userId)
                                }

                            } catch (cancelled: CancellationException) {
                                throw cancelled

                            } catch (error: Exception) {
                                createError =
                                    "Could not create account. Please try again."

                            } finally {
                                createPassword = ""
                                confirmPassword = ""
                                isBusy = false
                            }
                        }
                    }
                }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBusy) "Please wait..." else "Create account")
        }

        createError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CourtPalPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}