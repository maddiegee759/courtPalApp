package com.example.courtpalapp.feature.home

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.R
import com.example.courtpalapp.data.local.ActiveMatchEntity
import com.example.courtpalapp.data.local.CalendarEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(
    onStartMatchClick: () -> Unit = {},
    onRecordTrainingClick: () -> Unit = {},
    activeMatches: List<ActiveMatchEntity> = emptyList(),
    onContinueMatchClick: (Long) -> Unit = {},
    upcomingMatches: List<CalendarEventEntity> = emptyList(),
    onStartScheduledMatchClick: (Long) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.courtpal_logo),
            contentDescription = "CourtPal Logo",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "courtPal",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartMatchClick,
            modifier = Modifier.width(300.dp)
        ) {
            Text(
                text = "Start Tennis Match",
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRecordTrainingClick,
            modifier = Modifier.width(300.dp)
        ) {
            Text(
                text = "Record Training",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (upcomingMatches.isNotEmpty()) {

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Upcoming Matches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            upcomingMatches.forEach { event ->

                val opponent = event.friendName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Guest"

                val formattedDate = runCatching {
                    LocalDate.parse(event.date).format(
                        DateTimeFormatter.ofPattern(
                            "EEE, d MMM",
                            Locale.UK
                        )
                    )
                }.getOrDefault(event.date)

                Text(
                    text = "You vs $opponent",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$formattedDate at ${event.startTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onStartScheduledMatchClick(event.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Match")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (activeMatches.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Continue Matches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))


            activeMatches.forEach { match ->

                val lastEdited = Instant.ofEpochMilli(match.lastUpdated)
                    .atZone(ZoneId.systemDefault())
                    .format(
                        DateTimeFormatter.ofPattern(
                            "EEE, d MMM 'at' HH:mm",
                            Locale.UK
                        )
                    )

                Text(
                    text = "${match.player1Name} vs ${match.player2Name}",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Last edited: $lastEdited",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onContinueMatchClick(match.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue Match")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}