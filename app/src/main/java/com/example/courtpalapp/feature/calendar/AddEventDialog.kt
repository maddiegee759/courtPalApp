package com.example.courtpalapp.feature.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.common.formatUsername
import com.example.courtpalapp.data.local.CalendarEventEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A friend already linked to a CourtPal account. Do not populate with manual names. */
data class CalendarFriendOption(val userId: String, val username: String)


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent, Boolean, String?, String?) -> Unit,
    existingEvent: CalendarEventEntity? = null,
    courtPalFriends: List<CalendarFriendOption> = emptyList()
) {
    val context = LocalContext.current
    val isEditing = existingEvent != null
    var title by remember(existingEvent) { mutableStateOf(existingEvent?.title ?: "") }
    var location by remember(existingEvent) { mutableStateOf(existingEvent?.location ?: "") }
    var selectedDate by remember(existingEvent) {
        mutableStateOf(existingEvent?.date?.let(LocalDate::parse) ?: initialDate)
    }
    var startTime by remember(existingEvent) {
        mutableStateOf(existingEvent?.startTime?.let(LocalTime::parse) ?: LocalTime.of(14, 0))
    }
    var endTime by remember(existingEvent) {
        mutableStateOf(existingEvent?.endTime?.let(LocalTime::parse) ?: LocalTime.of(15, 0))
    }
    var syncToGoogle by remember { mutableStateOf(false) }
    // Blank name means solo. Selecting a dropdown entry links to a CourtPal friend;
    // typing a name manually never links an account.
    var friendName by remember(existingEvent) { mutableStateOf(existingEvent?.friendName ?: existingEvent?.friendId ?: "") }
    var selectedFriend by remember { mutableStateOf<CalendarFriendOption?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val matchingFriends = courtPalFriends.filter {
        it.username.contains(friendName.trim().removePrefix("@"), ignoreCase = true)
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Tennis Event" else "Create Tennis Event") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Event title") },
                    placeholder = { Text("Tennis with Alex") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            selectedDate = LocalDate.of(year, month + 1, day)
                        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Date: $selectedDate") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            startTime = LocalTime.of(hour, minute)
                        }, startTime.hour, startTime.minute, true).show()
                    }, modifier = Modifier.weight(1f)) { Text(startTime.format(timeFormatter)) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            endTime = LocalTime.of(hour, minute)
                        }, endTime.hour, endTime.minute, true).show()
                    }, modifier = Modifier.weight(1f)) { Text(endTime.format(timeFormatter)) }
                }
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Location") }, placeholder = { Text("Court 2") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded && matchingFriends.isNotEmpty() && !isEditing,
                    onExpandedChange = { if (!isEditing) dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedFriend != null) {
                            "@${friendName.removePrefix("@")}"
                        } else {
                            friendName
                        },
                        onValueChange = { typed ->
                            friendName = typed
                            selectedFriend = null
                            dropdownExpanded = typed.isNotBlank()
                        },
                        label = { Text("Friend's name (optional)") },
                        placeholder = { Text("Leave blank for solo, or enter a name") },
                        singleLine = true,
                        readOnly = isEditing,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded && matchingFriends.isNotEmpty() && !isEditing,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        matchingFriends.forEach { friend ->
                            DropdownMenuItem(
                                text = { Text(formatUsername(friend.username)) },
                                onClick = {
                                    friendName = friend.username
                                    selectedFriend = friend
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncToGoogle, onCheckedChange = { syncToGoogle = it })
                    Text("Add to Google Calendar", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && endTime.isAfter(startTime),
                onClick = {
                    val isLinked = selectedFriend != null
                    onSave(
                        CalendarEvent(
                            title = title.trim(), date = selectedDate,
                            startTime = startTime.format(timeFormatter),
                            endTime = endTime.format(timeFormatter),
                            location = location.trim(),
                            status = existingEvent?.status?.let(EventStatus::valueOf)
                                ?: if (isLinked) EventStatus.PENDING else EventStatus.CONFIRMED
                        ),
                        syncToGoogle && !isLinked,
                        friendName.trim().ifBlank { null },
                        selectedFriend?.userId
                    )
                }
            ) {
                Text(when {
                    isEditing -> "Save Changes"
                    selectedFriend != null -> "Send Proposal"
                    else -> "Create"
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
