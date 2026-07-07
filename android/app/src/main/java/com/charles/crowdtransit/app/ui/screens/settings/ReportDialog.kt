package com.charles.crowdtransit.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.charles.crowdtransit.app.BuildConfig
import com.charles.crowdtransit.app.ui.theme.AppBackground
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.ErrorContainer
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.Warning

@Composable
fun ReportDialog(
    state: ReportDialogState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIncludeDiagnosticsChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onDismissSuccess: () -> Unit,
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(state.imageUri) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            onImageSelected(it)
        }
    }

    LaunchedEffect(state.success) {
        if (state.success) {
            onDismissSuccess()
        }
    }

    Dialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Report a Problem",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurface,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = OnSurfaceSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorContainer),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Your report will be submitted to this app's GitHub issue tracker. " +
                                "Do not include passwords, private keys, medical information, financial information, " +
                                "or anything you do not want visible to the repository maintainers. " +
                                "If this repository is public, your report may be publicly visible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                if (!BuildConfig.GITHUB_API_TOKEN.isNotEmpty() || !BuildConfig.GITHUB_REPO_OWNER.isNotEmpty() || !BuildConfig.GITHUB_REPO_NAME.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "GitHub is not configured. Add github.api.token, github.repo.owner, and github.repo.name to local.properties.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("Title / Subject *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    enabled = !state.isSubmitting,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.includeDiagnostics,
                        onCheckedChange = onIncludeDiagnosticsChange,
                        colors = CheckboxDefaults.colors(checkedColor = Primary),
                        enabled = !state.isSubmitting,
                    )
                    Text(
                        text = "Include phone/app diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !state.isSubmitting,
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (selectedImageUri != null) "Change screenshot" else "Attach screenshot")
                    }
                    if (selectedImageUri != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                selectedImageUri = null
                                onImageSelected(null)
                            },
                            enabled = !state.isSubmitting,
                        ) {
                            Text("Remove", color = Error)
                        }
                    }
                }

                if (selectedImageUri != null) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }

                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        color = Error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.isSubmitting) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isSubmitting && state.title.isNotBlank() && state.description.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = OnSurface,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Submit")
                    }
                }
            }
        }
    }
}
