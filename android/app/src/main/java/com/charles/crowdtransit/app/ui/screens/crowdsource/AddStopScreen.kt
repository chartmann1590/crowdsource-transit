package com.charles.crowdtransit.app.ui.screens.crowdsource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.theme.OnPrimary
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.Secondary
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceCard
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStopScreen(
    userLat: Double,
    userLng: Double,
    onBack: () -> Unit,
    onSubmit: (name: String, lat: Double, lng: Double, transitTypes: List<String>, agencyName: String, notes: String, features: Map<String, Boolean>) -> Unit,
) {
    var stopName by remember { mutableStateOf("") }
    var stopCode by remember { mutableStateOf("") }
    var agencyName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pinLat by remember { mutableDoubleStateOf(userLat) }
    var pinLng by remember { mutableDoubleStateOf(userLng) }

    val transitTypeOptions = listOf("bus", "train", "subway", "ferry", "tram")
    val selectedTypes = remember { mutableStateListOf<String>() }

    val featureOptions = listOf("shelter", "bench", "lighting", "elevator", "bikeParking", "parking")
    val selectedFeatures = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a Stop", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Adding a Missing Stop", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Your submission will be reviewed by the community. Once 3 users verify it, the stop will be added.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                }
            }

            OutlinedTextField(
                value = stopName,
                onValueChange = { stopName = it },
                label = { Text("Stop Name *", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )

            OutlinedTextField(
                value = stopCode,
                onValueChange = { stopCode = it },
                label = { Text("Stop Code (optional)", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )

            Text("Transit Types *", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                transitTypeOptions.forEach { type ->
                    FilterChip(
                        selected = selectedTypes.contains(type),
                        onClick = {
                            if (selectedTypes.contains(type)) selectedTypes.remove(type)
                            else selectedTypes.add(type)
                        },
                        label = { Text(type.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = agencyName,
                onValueChange = { agencyName = it },
                label = { Text("Transit Agency Name", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )

            Text("Accessibility & Amenities", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            featureOptions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedFeatures[feature] ?: false,
                                onCheckedChange = { selectedFeatures[feature] = it },
                                colors = CheckboxDefaults.colors(checkedColor = Primary),
                            )
                            Text(
                                feature.replaceFirstChar { it.uppercase() },
                                color = OnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Notes", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Location", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Lat: %.6f, Lng: %.6f".format(pinLat, pinLng),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                    Text(
                        "(Your current location will be used. Walk to the stop first.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
            }

            Button(
                onClick = {
                    if (stopName.isNotBlank() && selectedTypes.isNotEmpty()) {
                        onSubmit(
                            stopName,
                            pinLat,
                            pinLng,
                            selectedTypes.toList(),
                            agencyName,
                            notes,
                            selectedFeatures.toMap(),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = stopName.isNotBlank() && selectedTypes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
            ) {
                Text("Submit Stop", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
