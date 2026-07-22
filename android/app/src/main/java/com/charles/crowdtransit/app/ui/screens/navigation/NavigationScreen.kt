package com.charles.crowdtransit.app.ui.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.data.navigation.NavigationSessionRepository
import com.charles.crowdtransit.app.domain.routing.PlanRequest
import com.charles.crowdtransit.app.domain.routing.PlanTripUseCase
import com.charles.crowdtransit.app.service.NavigationService
import com.charles.crowdtransit.app.ui.components.MapLibreView
import com.charles.crowdtransit.app.ui.screens.plan.planPolylines
import com.charles.crowdtransit.app.ui.screens.plan.planWalkStepMarkers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val REROUTE_DEBOUNCE_MS = 60_000L

@HiltViewModel
class NavigationViewModel @Inject constructor(
    val session: NavigationSessionRepository,
    private val planTrip: PlanTripUseCase,
) : ViewModel() {

    private val _rerouting = MutableStateFlow(false)
    val rerouting: StateFlow<Boolean> = _rerouting

    private val _rerouteMessage = MutableStateFlow<String?>(null)
    val rerouteMessage: StateFlow<String?> = _rerouteMessage

    /** Recompute the route from the current position (off-route recovery, ≥60 s apart). */
    fun reroute() {
        val fix = session.lastFix.value ?: return
        val plan = session.activePlan ?: return
        val now = System.currentTimeMillis()
        if (now - session.lastRerouteMs < REROUTE_DEBOUNCE_MS) {
            _rerouteMessage.value = "Just rerouted — give it a minute."
            return
        }
        _rerouting.value = true
        _rerouteMessage.value = null
        viewModelScope.launch {
            try {
                val plans = planTrip(
                    PlanRequest(
                        fromName = "Current location",
                        fromLat = fix.first,
                        fromLng = fix.second,
                        toName = plan.to.name,
                        toLat = plan.to.lat,
                        toLng = plan.to.lng,
                    ),
                )
                val newPlan = plans.firstOrNull()
                if (newPlan != null) {
                    session.reroute(newPlan)
                } else {
                    _rerouteMessage.value = "Couldn't find a new route from here."
                }
            } catch (_: Exception) {
                _rerouteMessage.value = "Rerouting failed — check your connection."
            } finally {
                _rerouting.value = false
            }
        }
    }
}

@Composable
fun NavigationScreen(
    onExit: () -> Unit,
    viewModel: NavigationViewModel = hiltViewModel(),
) {
    val state by viewModel.session.state.collectAsStateWithLifecycle()
    val fix by viewModel.session.lastFix.collectAsStateWithLifecycle()
    val rerouting by viewModel.rerouting.collectAsStateWithLifecycle()
    val rerouteMessage by viewModel.rerouteMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val plan = viewModel.session.activePlan

    Box(Modifier.fillMaxSize()) {
        if (plan != null) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                polylines = planPolylines(plan),
                fitToPolylines = false,
                walkStepMarkers = planWalkStepMarkers(plan),
                userLat = fix?.first,
                userLng = fix?.second,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val current = state
            if (current == null) {
                InstructionCard(title = "Navigation ended", subtitle = null, offRoute = false)
            } else {
                InstructionCard(
                    title = current.instruction,
                    subtitle = buildString {
                        if (current.distanceToNextM > 0) append("${current.distanceToNextM} m")
                        current.nextInstruction?.let {
                            if (isNotEmpty()) append(" · ")
                            append("Then: $it")
                        }
                    }.ifBlank { null },
                    offRoute = current.offRoute,
                )
                if (current.offRoute) {
                    Button(
                        onClick = viewModel::reroute,
                        enabled = !rerouting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(if (rerouting) "Rerouting…" else "Reroute from here")
                    }
                }
                rerouteMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    NavigationService.stop(context)
                    onExit()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state?.arrived == true) "Done" else "End navigation")
            }
        }
    }
}

@Composable
private fun InstructionCard(title: String, subtitle: String?, offRoute: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (offRoute) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (offRoute) {
            Text("OFF ROUTE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        subtitle?.let {
            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
