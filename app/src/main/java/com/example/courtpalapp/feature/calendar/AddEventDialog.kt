package com.example.courtpalapp.feature.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.data.local.CalendarEventEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AddEventDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent, Boolean, String?) -> Unit,
    existingEvent: CalendarEventEntity? = null
) {
    val context = LocalContext.current

    val isEditing = existingEvent != null

    var title by remember(existingEvent) {
        mutableStateOf(existingEvent?.title ?: "")
    }

    var location by remember(existingEvent) {
        mutableStateOf(existingEvent?.location ?: "")
    }

    var selectedDate by remember(existingEvent) {
        mutableStateOf(
            existingEvent?.date?.let(LocalDate::parse) ?: initialDate
        )
    }

    var startTime by remember(existingEvent) {
        mutableStateOf(
            existingEvent?.startTime?.let(LocalTime::parse)
                ?: LocalTime.of(14, 0)
        )
    }

    var endTime by remember(existingEvent) {
        mutableStateOf(
            existingEvent?.endTime?.let(LocalTime::parse)
                ?: LocalTime.of(15, 0)
        )
    }

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

    var syncToGoogle by remember {
        mutableStateOf(false)
    }

    var proposeToFriend by remember(existingEvent) {mutableStateOf(existingEvent?.friendId != null)}

    var friendName by remember(existingEvent) {mutableStateOf(existingEvent?.friendId ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (isEditing) Text("Edit Tennis Event")
            else Text("Create Tennis Event")
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

                if (!isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = proposeToFriend,
                            onCheckedChange = {
                                proposeToFriend = it
                            }
                        )

                        Spacer(Modifier.width(12.dp))

                        Text("Propose to a friend")
                    }
                }

                if (proposeToFriend) {
                    OutlinedTextField(
                        value = friendName,
                        onValueChange = { friendName = it },
                        label = { Text("Friend's name") },
                        placeholder = { Text("e.g. Alex") },
                        singleLine = true,
                        readOnly = isEditing,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "The event will remain pending until accepted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncToGoogle,
                        onCheckedChange = { syncToGoogle = it }
                    )

                    Text(
                        text = "Add to Google Calendar",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

            }
        },
        confirmButton = {

            TextButton(
                enabled = title.isNotBlank() &&
                        endTime.isAfter(startTime) &&
                        (!proposeToFriend || friendName.isNotBlank()),
                onClick = {
                    onSave(
                        CalendarEvent(
                            title = title.trim(),
                            date = selectedDate,
                            startTime = startTime.format(timeFormatter),
                            endTime = endTime.format(timeFormatter),
                            location = location.trim(),
                            status = existingEvent?.status
                                ?.let(EventStatus::valueOf)
                                ?: if (proposeToFriend) {
                                    EventStatus.PENDING
                                } else {
                                    EventStatus.CONFIRMED
                                }
                        ),
                        syncToGoogle && !proposeToFriend,
                        if (proposeToFriend) friendName.trim() else null
                    )
                }
            ) {
                Text(
                    when {
                        isEditing -> "Save Changes"
                        proposeToFriend -> "Send Proposal"
                        else -> "Create"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
