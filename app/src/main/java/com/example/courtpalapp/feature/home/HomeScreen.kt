package com.example.courtpalapp.feature.home


import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun HomeScreen(onStartMatchClick: () -> Unit = {}) {
    Text("Welcome to CourtPal!")

    Button(
        onClick = onStartMatchClick
    ) {
        Text("Start Tennis Match")
    }

}
