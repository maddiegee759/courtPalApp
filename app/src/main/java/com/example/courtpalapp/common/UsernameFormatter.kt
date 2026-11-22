package com.example.courtpalapp.common

fun formatUsername(username: String): String {
    return "@${username.removePrefix("@")}"
}