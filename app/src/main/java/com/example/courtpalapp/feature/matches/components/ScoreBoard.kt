package com.example.courtpalapp.feature.matches.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.domain.match.*

@Composable
fun ScoreBoard(
    score: TennisScore,
    settings: MatchSettings,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Scoreboard",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(2.5f))

                Row(
                    modifier = Modifier.weight(3f)
                ) {
                    ScoreHeader("SETS", Modifier.weight(1f))
                    ScoreHeader("GAMES", Modifier.weight(1f))
                    ScoreHeader("POINTS", Modifier.weight(1f))
                }
            }

            HorizontalDivider()

            PlayerScoreRow(
                name = settings.player1Name,
                sets = score.player1Sets,
                games = score.player1Games,
                points = TennisScoring.pointLabel(
                    score.player1Points,
                    score.player2Points,
                    score.isTieBreak,
                    settings.scoringMode
                ),
                isServing = score.currentServer == 1
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            PlayerScoreRow(
                name = settings.player2Name,
                sets = score.player2Sets,
                games = score.player2Games,
                points = TennisScoring.pointLabel(
                    score.player2Points,
                    score.player1Points,
                    score.isTieBreak,
                    settings.scoringMode
                ),
                isServing = score.currentServer == 2
            )

            if (score.completedSets.isNotEmpty()) {
                HorizontalDivider()

                Text(
                    "Completed sets",
                    style = MaterialTheme.typography.labelLarge
                )

                score.completedSets.forEachIndexed { index, set ->
                    Text(
                        "Set ${index + 1}: " +
                                "${set.player1Games}–${set.player2Games}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (score.isTieBreak) {
                Text(
                    "Tie-break",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ScoreHeader(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun PlayerScoreRow(
    name: String,
    sets: Int,
    games: Int,
    points: String,
    isServing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(2.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isServing) {
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.weight(3f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScoreNumber(sets.toString(), Modifier.weight(1f))
            ScoreNumber(games.toString(), Modifier.weight(1f))
            ScoreNumber(points, Modifier.weight(1f))
        }
    }
}


@Composable
private fun ScoreNumber(
    value: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
}
