package com.charles.crowdtransit.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.theme.AppBackground
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.LevelCommuter
import com.charles.crowdtransit.app.ui.theme.LevelConductor
import com.charles.crowdtransit.app.ui.theme.LevelPedestrian
import com.charles.crowdtransit.app.ui.theme.LevelRegular
import com.charles.crowdtransit.app.ui.theme.LevelTransitLegend
import com.charles.crowdtransit.app.ui.theme.OnPrimary
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceCard
import com.charles.crowdtransit.model.Badges
import com.charles.crowdtransit.model.Level

private fun colorForLevel(level: Level) = when (level) {
    Level.PEDESTRIAN -> LevelPedestrian
    Level.COMMUTER -> LevelCommuter
    Level.REGULAR -> LevelRegular
    Level.CONDUCTOR -> LevelConductor
    Level.TRANSIT_LEGEND -> LevelTransitLegend
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnSurface,
                ),
            )
        },
        containerColor = AppBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val levelColor = colorForLevel(uiState.level)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .border(4.dp, levelColor, CircleShape)
                    .padding(6.dp)
                    .background(Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.profile?.avatarInitials?.ifBlank { "R" } ?: "R",
                    color = OnPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Text(
                text = uiState.profile?.displayName ?: uiState.user?.displayName ?: "Rider",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = levelColor, modifier = Modifier.size(16.dp))
                Text(
                    text = "${uiState.level.displayName} · ${uiState.stats.points} pts",
                    style = MaterialTheme.typography.labelLarge,
                    color = levelColor,
                )
            }

            Text(
                text = if (uiState.user?.isAnonymous == true) "Anonymous" else uiState.user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceSecondary,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Your Stats", style = MaterialTheme.typography.titleMedium, color = OnSurface)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem(label = "Reviews", value = uiState.reviewCount.toString())
                        StatItem(label = "Stops Added", value = uiState.stopsAdded.toString())
                        StatItem(label = "Streak", value = "${uiState.stats.streakCount}d")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Badges", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    val earned = Badges.labels.keys.filter { uiState.badges[it] == true }
                    if (earned.isEmpty()) {
                        Text(
                            "Contribute to earn your first badge!",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceSecondary,
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.height(if (earned.size > 3) 160.dp else 84.dp),
                        ) {
                            items(earned) { badgeKey ->
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(PrimaryLight, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Primary)
                                    }
                                    Text(
                                        text = Badges.labels[badgeKey] ?: badgeKey,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceSecondary,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, MaterialTheme.shapes.large)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,

            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Primary)
                    Text("Leaderboard", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                }
                IconButton(onClick = onLeaderboardClick) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "View leaderboard", tint = OnSurfaceSecondary)
                }
            }

            Spacer(Modifier.weight(1f))

            if (uiState.user?.isAnonymous == true) {
                Button(
                    onClick = onSignInClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign In / Create Account")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.signOut()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Error, contentColor = OnPrimary),
                ) {
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = Primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceSecondary,
        )
    }
}
