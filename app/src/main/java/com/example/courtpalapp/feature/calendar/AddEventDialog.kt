package com.example.courtpalapp.feature.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AddEventDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf(LocalTime.of(14, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(15, 0)) }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val datePicker = DatePickerDialog(
        context,
        { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day)
        },
        selectedDate.year,
        selectedDate.monthValue - 1,
        selectedDate.dayOfMonth
    )

    val startTimePicker = TimePickerDialog(
        context,
        { _, hour, minute ->
            startTime = LocalTime.of(hour, minute)
        },
        startTime.hour,
        startTime.minute,
        true
    )

    val endTimePicker = TimePickerDialog(
        context,
        { _, hour, minute ->
            endTime = LocalTime.of(hour, minute)
        },
        endTime.hour,
        endTime.minute,
        true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Tennis Event")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event title") },
                    placeholder = { Text("Tennis with Alex") },
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { datePicker.show() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: $selectedDate")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { startTimePicker.show() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(startTime.format(timeFormatter))
                    }

                    OutlinedButton(
                        onClick = { endTimePicker.show() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(endTime.format(timeFormatter))
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    placeholder = { Text("Court 2") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() &&
                        endTime.isAfter(startTime),
                onClick = {
                    onSave(
                        CalendarEvent(
                            title = title.trim(),
                            date = selectedDate,
                            startTime = startTime.format(timeFormatter),
                            endTime = endTime.format(timeFormatter),
                            location = location.trim(),
                            status = EventStatus.CONFIRMED
                        )
                    )
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
