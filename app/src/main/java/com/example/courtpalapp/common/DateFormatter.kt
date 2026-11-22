package com.example.courtpalapp.common

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
private val displayDateFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.UK)

@RequiresApi(Build.VERSION_CODES.O)
fun formatDate(date: String): String {
    return LocalDate.parse(date).format(displayDateFormatter)
}

@RequiresApi(Build.VERSION_CODES.O)
fun formatDateTime(date: String, time: String): String {
    return "${formatDate(date)} $time"
}