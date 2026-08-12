package com.charles.crowdtransit.app.ui.screens.auth

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ai.device.DeviceCapability
import com.charles.crowdtransit.app.ai.device.DeviceProbe
import com.charles.crowdtransit.app.ui.assistant.HopperMascot
import com.charles.crowdtransit.app.ui.assistant.MascotState
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // Skipped entirely on devices below Hopper's capability floor.
    val assistantSupported = remember { DeviceProbe(context).tier() != DeviceCapability.Tier.Unsupported }
    var showHopperStep by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted
        if (assistantSupported) showHopperStep = true else { viewModel.markOnboardingComplete(); onFinish() }
    }

    if (showHopperStep) {
        HopperOnboardingStep(
            onFinish = {
                viewModel.markOnboardingComplete()
                onFinish()
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CrowdTransit",
            style = MaterialTheme.typography.displayLarge,
            color = OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Find and rate transit stops near you.\nReal-time info from the community.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, MaterialTheme.shapes.large)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "We'll ask for your location to show transit stops near you.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceSecondary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (!locationGranted) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else if (assistantSupported) {
                    showHopperStep = true
                } else {
                    viewModel.markOnboardingComplete()
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = uiState.isAuthenticated || !uiState.isLoading,
        ) {
            Text(
                text = if (uiState.isLoading) "Signing in..." else "Get Started",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (uiState.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * "Meet Hopper" opt-in step, shown after location permission on capable devices only
 * (see [DeviceCapability]). Enabling here does not download anything — the model
 * download is a separate, explicit action from Settings, guarded by its own
 * cellular-network confirmation.
 */
@Composable
private fun HopperOnboardingStep(onFinish: () -> Unit) {
    val viewModel: HopperOnboardingViewModel = hiltViewModel()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HopperMascot(state = MascotState.Idle, size = 120.dp)

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Meet Hopper",
            style = MaterialTheme.typography.displaySmall,
            color = OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Hopper is an optional AI assistant that can help plan your route and " +
                "answer questions about the trip you're on — entirely on your device. " +
                "Nothing you say to Hopper ever leaves your phone.\n\n" +
                "It's a free download (2+ GB) you can start any time from Settings, and " +
                "you can turn it off or delete it whenever you like.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.enable()
                onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Enable Hopper", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onFinish) {
            Text("Not now", color = OnSurfaceSecondary)
        }
    }
}
