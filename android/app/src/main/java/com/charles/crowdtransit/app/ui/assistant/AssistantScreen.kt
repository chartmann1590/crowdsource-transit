package com.charles.crowdtransit.app.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.ai.chat.AssistantChatMessage
import com.charles.crowdtransit.app.ai.engine.AssistantEngineState
import com.charles.crowdtransit.app.ai.model.AssistantModelCatalog
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated
import java.io.File

private val QUICK_REPLIES = listOf(
    "What's my next stop?",
    "I missed my bus, now what?",
    "Plan a trip",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onSetUpHopper: () -> Unit,
    onOpenItinerary: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val installedVariant by viewModel.installedVariant.collectAsStateWithLifecycle()

    // Gate the whole chat behind an actually-installed model — dropping the user into a
    // chat UI that can only ever reply "not set up" is worse than not showing it at all.
    if (installedVariant == null) {
        AssistantSetupPrompt(onBack = onBack, onSetUpHopper = onSetUpHopper)
        return
    }

    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val draftReply by viewModel.draftReply.collectAsStateWithLifecycle()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null) {
            val prompt = input.ifBlank { "What route or stop does this sign show?" }
            input = ""
            viewModel.sendMessage(prompt, imagePath = file.absolutePath)
        } else {
            file?.delete()
        }
    }

    fun launchCameraCapture() {
        val file = createCaptureFile(context)
        pendingCaptureFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    // TakePicture() starts the system camera app directly via an IMAGE_CAPTURE intent —
    // Android still enforces the caller's own CAMERA permission for that (Permission
    // Denial: revoked permission android.permission.CAMERA), confirmed on a real device,
    // so the runtime request can't be skipped even though we never touch the Camera API.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture()
    }

    val mascotState = when {
        engineState is AssistantEngineState.Loading -> MascotState.Thinking
        engineState is AssistantEngineState.Error && !isSending -> MascotState.Sleeping
        isSending && draftReply.isNullOrEmpty() -> MascotState.Thinking
        isSending -> MascotState.Talking
        else -> MascotState.Idle
    }

    val itemCount = messages.size + if (draftReply != null) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HopperMascot(state = mascotState, size = 36.dp)
                        Text("Hopper", color = OnSurface)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear chat", tint = OnSurfaceSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Surface,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty() && draftReply == null) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                            HopperMascot(state = mascotState, size = 96.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Ask Hopper about your route, or what to do next.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceSecondary,
                            )
                        }
                    }
                }
                itemsIndexed(messages) { _, message -> MessageBubble(message) }
                if (draftReply != null) {
                    item { MessageBubble(AssistantChatMessage("model", draftReply.orEmpty(), 0L)) }
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        items(QUICK_REPLIES) { reply ->
                            SuggestionChip(
                                onClick = { viewModel.sendMessage(reply) },
                                label = { Text(reply, style = MaterialTheme.typography.labelMedium) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceElevated),
                            )
                        }
                    }
                }
            }

            if (hasPlan) {
                Card(
                    onClick = onOpenItinerary,
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Directions, contentDescription = null, tint = Primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("View planned trip", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                "See the map, steps, and start navigation",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceSecondary,
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceSecondary)
                    }
                }
            }

            Text(
                "Hopper can be wrong — check with your transit agency.",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (installedVariant == AssistantModelCatalog.Variant.Multimodal) {
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                launchCameraCapture()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !isSending,
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Scan a stop or station sign", tint = OnSurfaceSecondary)
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Hopper…") },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        val text = input
                        input = ""
                        viewModel.sendMessage(text)
                    },
                    enabled = input.isNotBlank() && !isSending,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Primary)
                }
            }
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat history?") },
            text = { Text("This deletes Hopper's chat history on this device. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

/** Temp file for one camera capture — declared under res/xml/file_paths.xml's
 * "assistant_captures" cache-path, deleted right after Hopper responds (see
 * AssistantViewModel.sendMessage) or immediately if the capture is cancelled. */
private fun createCaptureFile(context: android.content.Context): File {
    val dir = File(context.cacheDir, "assistant").apply { mkdirs() }
    return File(dir, "capture_${System.currentTimeMillis()}.jpg")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSetupPrompt(onBack: () -> Unit, onSetUpHopper: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hopper") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Surface,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HopperMascot(state = MascotState.Sleeping, size = 120.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "Hopper isn't set up yet",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Hopper chats about your route entirely on your device, but it needs its " +
                    "AI model downloaded first — a one-time download, a couple GB depending " +
                    "on the option you pick.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onSetUpHopper,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("Set up Hopper")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AssistantChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isUser) PrimaryLight else SurfaceElevated),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = message.text.ifBlank { "…" },
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
