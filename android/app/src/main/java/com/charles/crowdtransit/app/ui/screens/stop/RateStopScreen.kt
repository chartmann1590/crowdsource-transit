package com.charles.crowdtransit.app.ui.screens.stop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.crowdtransit.app.ui.components.StarRating
import com.charles.crowdtransit.app.ui.theme.OnPrimary
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceDark
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateStopScreen(
    stopId: String,
    stopName: String,
    onBack: () -> Unit,
    onSubmit: (overall: Int, subcategories: Map<String, Int>, text: String, transitType: String, isAnonymous: Boolean) -> Unit,
) {
    var overallRating by remember { mutableIntStateOf(0) }
    var cleanlinessRating by remember { mutableIntStateOf(0) }
    var safetyRating by remember { mutableIntStateOf(0) }
    var accessibilityRating by remember { mutableIntStateOf(0) }
    var reliabilityRating by remember { mutableIntStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    var selectedTransitType by remember { mutableStateOf("bus") }
    var postAnonymously by remember { mutableStateOf(false) }

    val transitTypes = listOf("bus", "train", "subway", "ferry", "tram")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Write a Review", color = OnSurface) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stopName, style = MaterialTheme.typography.titleLarge, color = OnSurface)

            Text("Overall Rating", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            StarRating(
                rating = overallRating.toFloat(),
                starSize = 40.dp,
                interactive = true,
                onRatingChange = { overallRating = it },
            )

            Divider(color = SurfaceElevated)

            Text("Rate by Category", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            listOf(
                "Cleanliness" to cleanlinessRating,
                "Safety" to safetyRating,
                "Accessibility" to accessibilityRating,
                "Reliability" to reliabilityRating,
            ).forEachIndexed { i, (label, rating) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    StarRating(
                        rating = rating.toFloat(),
                        starSize = 24.dp,
                        interactive = true,
                        onRatingChange = { newVal ->
                            when (i) {
                                0 -> cleanlinessRating = newVal
                                1 -> safetyRating = newVal
                                2 -> accessibilityRating = newVal
                                3 -> reliabilityRating = newVal
                            }
                        },
                    )
                }
            }

            Divider(color = SurfaceElevated)

            Text("Your Experience", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Share your experience at this stop...", color = OnSurfaceSecondary) },
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    cursorColor = Primary,
                ),
            )

            Text("What did you ride?", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                transitTypes.forEach { type ->
                    FilterChip(
                        selected = selectedTransitType == type,
                        onClick = { selectedTransitType = type },
                        label = { Text(type.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Post anonymously", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                Switch(
                    checked = postAnonymously,
                    onCheckedChange = { postAnonymously = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Primary),
                )
            }

            Button(
                onClick = {
                    if (overallRating > 0) {
                        onSubmit(
                            overallRating,
                            mapOf(
                                "cleanliness" to cleanlinessRating,
                                "safety" to safetyRating,
                                "accessibility" to accessibilityRating,
                                "reliability" to reliabilityRating,
                            ),
                            commentText,
                            selectedTransitType,
                            postAnonymously,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = overallRating > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("Submit Review", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
            }
        }
    }
}
