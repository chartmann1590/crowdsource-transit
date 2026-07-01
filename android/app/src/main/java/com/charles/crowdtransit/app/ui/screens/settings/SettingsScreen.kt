package com.charles.crowdtransit.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val useImperial by viewModel.useImperialUnits.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnSurface,
                ),
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "Units",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Distance units",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface,
                    )
                    Text(
                        text = if (useImperial) "Displaying miles and feet" else "Displaying kilometers and meters",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = useImperial,
                    onCheckedChange = { viewModel.toggleUnits() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Primary),
                )
            }

            Spacer(Modifier.height(24.dp))
            Divider(color = OnSurfaceSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDownloadsClick)
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Offline Downloads", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "Download agencies for offline use",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OnSurfaceSecondary)
            }
        }
    }
}
