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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.charles.crowdtransit.app.data.feedback.GithubComment
import com.charles.crowdtransit.app.data.feedback.GithubIssue
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.OnSurfaceFaint
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.Success
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceElevated

@Composable
fun IssueDetailDialog(
    state: IssueDetailState,
    issueNumber: Int,
    issueTitle: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onReplyTextChange: (String) -> Unit,
    onReplyImageSelected: (Uri?) -> Unit,
    onPostComment: () -> Unit,
) {
    var selectedReplyImageUri by remember { mutableStateOf<Uri?>(state.replyImageUri) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedReplyImageUri = it
            onReplyImageSelected(it)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = issueTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#$issueNumber",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceSecondary,
                            )
                            if (state.issue != null) {
                                Spacer(Modifier.width(8.dp))
                                val color = if (state.issue.state == "open") Success else Error
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = color.copy(alpha = 0.15f),
                                    ),
                                ) {
                                    Text(
                                        text = state.issue.state.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = OnSurfaceSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = OnSurfaceSecondary)
                    }
                }

                HorizontalDivider(color = OnSurfaceSecondary.copy(alpha = 0.15f))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Primary)
                            }
                        }
                    } else if (state.error != null && state.issue == null) {
                        item {
                            Text(
                                text = state.error,
                                color = Error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        if (state.issue != null) {
                            item {
                                IssueBodyCard(issue = state.issue)
                            }
                        }
                        item {
                            Text(
                                text = "Comments",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                            )
                        }
                        if (state.comments.isEmpty() && !state.isLoading) {
                            item {
                                Text(
                                    text = "No comments yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceFaint,
                                )
                            }
                        }
                        items(state.comments) { comment ->
                            CommentCard(comment = comment)
                        }
                    }
                }

                HorizontalDivider(color = OnSurfaceSecondary.copy(alpha = 0.15f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (state.error != null && state.issue != null) {
                        Text(
                            text = state.error,
                            color = Error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !state.isPostingComment,
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (selectedReplyImageUri != null) "Change" else "Attach", style = MaterialTheme.typography.labelSmall)
                        }
                        if (selectedReplyImageUri != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    selectedReplyImageUri = null
                                    onReplyImageSelected(null)
                                },
                                enabled = !state.isPostingComment,
                            ) {
                                Text("Remove", color = Error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (selectedReplyImageUri != null) {
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model = selectedReplyImageUri,
                            contentDescription = "Reply screenshot",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedTextField(
                            value = state.replyText,
                            onValueChange = onReplyTextChange,
                            placeholder = { Text("Write a reply...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            enabled = !state.isPostingComment,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onPostComment,
                            enabled = !state.isPostingComment && state.replyText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            if (state.isPostingComment) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueBodyCard(issue: GithubIssue) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = issue.body ?: "No description",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun CommentCard(comment: GithubComment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = comment.user.login,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = comment.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceFaint,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
            )
        }
    }
}
