package com.charles.crowdtransit.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ai.device.DeviceCapability
import com.charles.crowdtransit.app.ai.model.AssistantModelCatalog
import com.charles.crowdtransit.app.ai.model.AssistantModelDownloader
import com.charles.crowdtransit.app.ui.assistant.HopperMascot
import com.charles.crowdtransit.app.ui.assistant.MascotState
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSettingsScreen(
    onBack: () -> Unit,
    viewModel: AssistantSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = OnSurface),
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HopperMascot(state = MascotState.Idle, size = 56.dp)
                Column {
                    Text("Hopper", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                    Text(
                        "Runs entirely on your phone — nothing you say to Hopper leaves this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when (uiState.tier) {
                DeviceCapability.Tier.Unsupported -> {
                    Text(
                        "Hopper isn't available on this device — it needs Android 12 or newer and enough memory to run an on-device AI model.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                }

                else -> {
                    if (uiState.tier == DeviceCapability.Tier.Basic) {
                        Text(
                            "Your phone is on the lighter side, so Hopper will think slowly and only handle text. Replies may take 10–20 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    uiState.offeredVariants.forEach { variant ->
                        VariantCard(
                            variant = variant,
                            recommended = variant == uiState.recommendedVariant,
                            installed = uiState.installedVariant == variant,
                            downloadState = uiState.downloadState,
                            onDownload = { viewModel.download(variant) },
                            onCancel = { viewModel.cancelDownload() },
                            onDelete = { viewModel.delete(variant) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    Divider(color = OnSurfaceSecondary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Hopper", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                            Text(
                                "Show Hopper's icon on the map and in trip screens",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceSecondary,
                            )
                        }
                        Switch(
                            checked = uiState.enabled,
                            onCheckedChange = { viewModel.setEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Primary),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text("Clear chat history", color = Error)
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Gemma is provided by Google and licensed under Apache 2.0.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantCard(
    variant: AssistantModelCatalog.Variant,
    recommended: Boolean,
    installed: Boolean,
    downloadState: AssistantModelDownloader.State,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = AssistantModelCatalog.byVariant(variant)
    val running = downloadState as? AssistantModelDownloader.State.Running
    val isThisRunning = running?.info?.variant == variant
    val verifying = (downloadState as? AssistantModelDownloader.State.Verifying)?.info?.variant == variant
    val failed = downloadState as? AssistantModelDownloader.State.Failed
    val sizeMb = info.sizeBytes / 1_000_000

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.label, style = MaterialTheme.typography.titleSmall, color = OnSurface, modifier = Modifier.weight(1f))
                if (recommended) {
                    Text("Recommended", style = MaterialTheme.typography.labelSmall, color = Primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(info.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceSecondary)
            Spacer(Modifier.height(4.dp))
            Text("${sizeMb} MB download", style = MaterialTheme.typography.labelSmall, color = OnSurfaceSecondary)
            Spacer(Modifier.height(10.dp))

            when {
                isThisRunning -> {
                    val pct = if (info.sizeBytes > 0) (running.downloadedBytes * 100 / info.sizeBytes).toInt() else 0
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$pct%", style = MaterialTheme.typography.labelSmall, color = OnSurfaceSecondary)
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }

                verifying -> Text("Verifying…", style = MaterialTheme.typography.labelSmall, color = OnSurfaceSecondary)

                installed -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Downloaded", style = MaterialTheme.typography.labelMedium, color = Primary, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                }

                else -> {
                    if (failed?.info?.variant == variant) {
                        Text(failed.message, style = MaterialTheme.typography.labelSmall, color = Error)
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("Download")
                    }
                }
            }
        }
    }
}
