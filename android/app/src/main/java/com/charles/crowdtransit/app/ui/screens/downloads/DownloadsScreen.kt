package com.charles.crowdtransit.app.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.Success
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceCard
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedAgencies.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Downloads") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Download an agency's stops so search and nearby stops still work offline or when Transitland is unreachable.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceSecondary,
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Search transit agencies") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    cursorColor = Primary,
                ),
            )

            Button(onClick = viewModel::search, modifier = Modifier.fillMaxWidth()) {
                Text("Search")
            }

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (uiState.isSearching) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.padding(8.dp))
            }

            if (uiState.searchResults.isNotEmpty()) {
                Text("Search Results", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.searchResults, key = { it.onestopId }) { operator ->
                        val isDownloading = uiState.downloadingOnestopId == operator.onestopId
                        val alreadyDownloaded = downloaded.any { it.onestopId == operator.onestopId }
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        operator.name ?: operator.shortName ?: operator.onestopId,
                                        color = OnSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (isDownloading) {
                                        Text(
                                            "Downloading... ${uiState.downloadedCount} stops",
                                            color = OnSurfaceSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                when {
                                    isDownloading -> CircularProgressIndicator(
                                        modifier = Modifier.height(24.dp),
                                        color = Primary,
                                    )
                                    alreadyDownloaded -> Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = Success,
                                    )
                                    else -> IconButton(onClick = { viewModel.download(operator) }) {
                                        Icon(Icons.Filled.Download, contentDescription = "Download", tint = Primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Downloaded Agencies", style = MaterialTheme.typography.titleSmall, color = OnSurface)

            if (downloaded.isEmpty()) {
                Text(
                    "No agencies downloaded yet.",
                    color = OnSurfaceSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(downloaded, key = { it.onestopId }) { agency ->
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(agency.name, color = OnSurface, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${agency.stopCount} stops",
                                        color = OnSurfaceSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                IconButton(onClick = { viewModel.remove(agency.onestopId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
