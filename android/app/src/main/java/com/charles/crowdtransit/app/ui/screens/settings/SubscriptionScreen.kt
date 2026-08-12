package com.charles.crowdtransit.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

private val PERKS = listOf(
    "No banner ads on the map",
    "No interstitial ads when opening a stop",
    "Support CrowdTransit's development",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remove Ads") },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            if (uiState.isSubscribed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Primary)
                    Text("You're subscribed — ads are off", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Thanks for supporting CrowdTransit. Manage or cancel anytime from Google Play's subscriptions page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceSecondary,
                )
            } else {
                Text("Ride without interruptions", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                Spacer(Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        PERKS.forEach { perk ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Primary, modifier = Modifier.height(18.dp))
                                Spacer(Modifier.height(0.dp))
                                Text(perk, style = MaterialTheme.typography.bodyMedium, color = OnSurface, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                val price = uiState.price
                if (price == null) {
                    Text(
                        "Remove Ads isn't available yet — check back soon.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                } else {
                    Button(
                        onClick = { (context as? android.app.Activity)?.let { viewModel.subscribe(it) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text("Subscribe — $price / ${uiState.period ?: "month"}")
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { viewModel.restorePurchases() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore purchases")
                }
            }
        }
    }
}
