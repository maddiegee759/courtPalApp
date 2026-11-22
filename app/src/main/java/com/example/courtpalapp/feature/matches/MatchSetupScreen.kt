package com.example.courtpalapp.feature.matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.courtpalapp.domain.match.MatchMode
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.ScoringMode
import kotlin.random.Random
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.example.courtpalapp.data.local.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSetupScreen(
    onStartMatch: (MatchSettings) -> Unit,
    onAddFriend: () -> Unit = {},
    friends: List<Pair<String, String>> = emptyList(),
    initialPlayer2Name: String = "Guest",
    initialPlayer2Id: String? = null,
    errorMessage: String? = null,
    defaultPreferences: UserPreferences = UserPreferences()
) {
    var player1Name by rememberSaveable {
        mutableStateOf("You")
    }

    var player2Name by rememberSaveable(initialPlayer2Name) {
        mutableStateOf(initialPlayer2Name)
    }

    var player2Id by rememberSaveable(initialPlayer2Id) {
        mutableStateOf(initialPlayer2Id)
    }

    var bestOfSets by rememberSaveable {
        mutableIntStateOf(defaultPreferences.matchFormat.toIntOrNull() ?: 3)
    }

    var gamesPerSet by rememberSaveable {
        mutableIntStateOf(6)
    }

    var advantageScoring by rememberSaveable {
        mutableStateOf(true)
    }

    var tieBreakEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var trackRallies by rememberSaveable {
        mutableStateOf(defaultPreferences.rallyTracking)
    }

    var coinFlipEnabled by rememberSaveable {
        mutableStateOf(false)
    }

    var firstServer by rememberSaveable {
        mutableIntStateOf(1)
    }

    var matchMode by rememberSaveable {
        mutableStateOf(
            if (defaultPreferences.scoringMode == "MANUAL")
                MatchMode.MANUAL
            else
                MatchMode.LIVE
        )
    }

    var showAdvanced by rememberSaveable {
        mutableStateOf(false)
    }

    var showFriendMenu by remember {
        mutableStateOf(false)
    }

    var showCoinFlipDialog by remember {
        mutableStateOf(false)
    }

    var coinCallPlayer by rememberSaveable {
        mutableIntStateOf(1)
    }

    var coinCallHeads by rememberSaveable {
        mutableStateOf(true)
    }

    var coinResultHeads by rememberSaveable {
        mutableStateOf<Boolean?>(null)
    }

    var tossWinner by rememberSaveable {
        mutableStateOf<Int?>(null)
    }

    var tossChoice by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // Derive the first server from the toss winner and their decision.
    val coinTossFirstServer = when (tossChoice) {
        "Serve" -> tossWinner ?: 1
        "Receive" -> if (tossWinner == 1) 2 else 1
        else -> 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "New Match",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Basic settings",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = player1Name,
            onValueChange = { player1Name = it },
            label = { Text("Player 1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenuBox(
            expanded = showFriendMenu,
            onExpandedChange = {
                showFriendMenu = !showFriendMenu
            }
        ) {
            OutlinedTextField(
                value = player2Name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Player 2") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = showFriendMenu
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable
                    )
            )

            ExposedDropdownMenu(
                expanded = showFriendMenu,
                onDismissRequest = {
                    showFriendMenu = false
                }
            ) {

                DropdownMenuItem(
                    text = { Text("Guest") },
                    onClick = {
                        player2Name = "Guest"
                        player2Id = null
                        showFriendMenu = false
                    }
                )

                friends.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            player2Name = name
                            player2Id = id
                            showFriendMenu = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("+ Add friend") },
                    onClick = {
                        showFriendMenu = false
                        onAddFriend()
                    }
                )
            }
        }

        if (player2Id == null) {
            OutlinedTextField(
                value = player2Name,
                onValueChange = { player2Name = it },
                label = { Text("Guest name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Text(
            text = "Best of",
            style = MaterialTheme.typography.titleSmall
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(1, 3, 5).forEachIndexed { index, value ->
                SegmentedButton(
                    selected = bestOfSets == value,
                    onClick = { bestOfSets = value },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = 3
                    ),
                    label = { Text("$value") }
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "Serving",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Flip coin to decide serve")

            Switch(
                checked = coinFlipEnabled,
                onCheckedChange = {
                    coinFlipEnabled = it
                }
            )
        }

        if (!coinFlipEnabled) {
            Text("First server")

            Row {
                RadioButton(
                    selected = firstServer == 1,
                    onClick = { firstServer = 1 }
                )
                Text(
                    text = player1Name,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Spacer(Modifier.width(16.dp))

                RadioButton(
                    selected = firstServer == 2,
                    onClick = { firstServer = 2 }
                )
                Text(
                    text = player2Name,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        HorizontalDivider()

        TextButton(
            onClick = {
                showAdvanced = !showAdvanced
            }
        ) {
            Text(
                if (showAdvanced)
                    "Hide advanced settings"
                else
                    "Show advanced settings"
            )
        }

        if (showAdvanced) {

            Text(
                text = "Advanced settings",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Advantage scoring")

                Switch(
                    checked = advantageScoring,
                    onCheckedChange = {
                        advantageScoring = it
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Track rally shots")

                Switch(
                    checked = trackRallies,
                    onCheckedChange = {
                        trackRallies = it
                    }
                )
            }

            Text("Games per set")

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(4, 6, 8).forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = gamesPerSet == value,
                        onClick = { gamesPerSet = value },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = 3
                        ),
                        label = { Text("$value") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tie-break enabled")

                Switch(
                    checked = tieBreakEnabled,
                    onCheckedChange = {
                        tieBreakEnabled = it
                    }
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "Tracking mode",
            style = MaterialTheme.typography.titleMedium
        )

        Row {
            RadioButton(
                selected = matchMode == MatchMode.LIVE,
                onClick = {
                    matchMode = MatchMode.LIVE
                }
            )
            Text(
                text = "Live",
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(Modifier.width(24.dp))

            RadioButton(
                selected = matchMode == MatchMode.MANUAL,
                onClick = {
                    matchMode = MatchMode.MANUAL
                }
            )
            Text(
                text = "Manual",
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = {
                if (coinFlipEnabled) {
                    coinResultHeads = null
                    tossWinner = null
                    tossChoice = null
                    showCoinFlipDialog = true
                } else {
                    onStartMatch(
                        MatchSettings(
                            player1Name = player1Name.trim(),
                            player2Name = player2Name.trim(),
                            player2Id = player2Id,
                            bestOfSets = bestOfSets,
                            gamesPerSet = gamesPerSet,
                            scoringMode =
                                if (advantageScoring)
                                    ScoringMode.ADVANTAGE
                                else
                                    ScoringMode.NO_ADVANTAGE,
                            tieBreakEnabled = tieBreakEnabled,
                            trackRallies = trackRallies,
                            matchMode = matchMode,
                            coinFlipEnabled = false,
                            firstServer = firstServer
                        )
                    )
                }
            },
            enabled = player1Name.isNotBlank() &&
                    player2Name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (matchMode == MatchMode.LIVE)
                    "Start Match"
                else
                    "Enter Result"
            )
        }
    }


    if (showCoinFlipDialog) {
        AlertDialog(
            onDismissRequest = {
                showCoinFlipDialog = false
            },
            title = {
                Text("Coin toss")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (coinResultHeads == null) {
                        Text("$player1Name, call the coin:")

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = coinCallHeads,
                                onClick = { coinCallHeads = true }
                            )
                            Text("Heads")

                            Spacer(Modifier.width(16.dp))

                            RadioButton(
                                selected = !coinCallHeads,
                                onClick = { coinCallHeads = false }
                            )
                            Text("Tails")
                        }
                    } else {
                        Text(
                            "Result: " +
                                    if (coinResultHeads == true)
                                        "Heads"
                                    else
                                        "Tails",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "${if (tossWinner == 1) player1Name else player2Name} won the toss!"
                        )

                        Text("Toss winner chooses:")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("Serve", "Receive").forEach { choice ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .selectable(
                                            selected = tossChoice == choice,
                                            onClick = { tossChoice = choice },
                                            role = Role.RadioButton
                                        )
                                ) {
                                    RadioButton(
                                        selected = tossChoice == choice,
                                        onClick = null
                                    )

                                    Text(
                                        text = choice,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        if (tossChoice != null) {
                            Text(
                                "First server: " +
                                        if (coinTossFirstServer == 1)
                                            player1Name
                                        else
                                            player2Name
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = coinResultHeads == null ||
                            tossChoice != null,
                    onClick = {
                        if (coinResultHeads == null) {
                            coinResultHeads = Random.nextBoolean()

                            tossWinner =
                                if (coinResultHeads == coinCallHeads)
                                    coinCallPlayer
                                else
                                    if (coinCallPlayer == 1) 2 else 1
                        } else {
                            showCoinFlipDialog = false

                            onStartMatch(
                                MatchSettings(
                                    player1Name = player1Name.trim(),
                                    player2Name = player2Name.trim(),
                                    player2Id = player2Id,
                                    bestOfSets = bestOfSets,
                                    gamesPerSet = gamesPerSet,
                                    scoringMode =
                                        if (advantageScoring)
                                            ScoringMode.ADVANTAGE
                                        else
                                            ScoringMode.NO_ADVANTAGE,
                                    tieBreakEnabled = tieBreakEnabled,
                                    trackRallies = trackRallies,
                                    matchMode = matchMode,
                                    coinFlipEnabled = true,
                                    firstServer = coinTossFirstServer
                                )
                            )
                        }
                    }
                ) {
                    Text(
                        if (coinResultHeads == null)
                            "Flip Coin"
                        else
                            "Continue"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCoinFlipDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}