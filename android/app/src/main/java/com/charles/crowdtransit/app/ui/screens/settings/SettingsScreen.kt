package com.charles.crowdtransit.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.crowdtransit.app.data.feedback.BugReport
import com.charles.crowdtransit.app.ui.theme.Error
import com.charles.crowdtransit.app.ui.theme.OnSurface
import com.charles.crowdtransit.app.ui.theme.OnSurfaceFaint
import com.charles.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.PrimaryLight
import com.charles.crowdtransit.app.ui.theme.Success
import com.charles.crowdtransit.app.ui.theme.Surface
import com.charles.crowdtransit.app.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    feedbackViewModel: FeedbackViewModel = hiltViewModel(),
) {
    val useImperial by settingsViewModel.useImperialUnits.collectAsStateWithLifecycle()

    val showReportDialog by feedbackViewModel.showReportDialog.collectAsState()
    val reportState by feedbackViewModel.reportState.collectAsState()

    val selectedReport by feedbackViewModel.selectedReport.collectAsState()
    val issueDetailState by feedbackViewModel.issueDetailState.collectAsState()

    val bugReports by feedbackViewModel.bugReports.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Units",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Distance units",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface,
                    )
                    Text(
                        text = if (useImperial) "Displaying miles and feet" else "Displaying kilometers and meters",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = useImperial,
                    onCheckedChange = { settingsViewModel.toggleUnits() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Primary),
                )
            }

            Spacer(Modifier.height(24.dp))
            Divider(color = OnSurfaceSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDownloadsClick)
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = Primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Offline Downloads", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "Download agencies for offline use",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OnSurfaceSecondary)
            }

            Spacer(Modifier.height(24.dp))
            Divider(color = OnSurfaceSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Support & Feedback",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { feedbackViewModel.openReportDialog() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Report a Problem")
            }

            Spacer(Modifier.height(16.dp))

            if (bugReports.isNotEmpty()) {
                Text(
                    text = "Submitted Reports",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                )
                Spacer(Modifier.height(8.dp))

                bugReports.forEach { report ->
                    ReportRow(
                        report = report,
                        onClick = { feedbackViewModel.openIssueDetail(report) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showReportDialog || reportState.success || reportState.isSubmitting) {
        ReportDialog(
            state = reportState,
            onTitleChange = { feedbackViewModel.updateReportTitle(it) },
            onDescriptionChange = { feedbackViewModel.updateReportDescription(it) },
            onIncludeDiagnosticsChange = { feedbackViewModel.updateReportIncludeDiagnostics(it) },
            onNameChange = { feedbackViewModel.updateReportName(it) },
            onEmailChange = { feedbackViewModel.updateReportEmail(it) },
            onImageSelected = { feedbackViewModel.setReportImage(it) },
            onSubmit = { feedbackViewModel.submitReport() },
            onDismiss = {
                feedbackViewModel.closeReportDialog()
                feedbackViewModel.dismissSuccess()
            },
            onDismissSuccess = { /* handled by LaunchedEffect in dialog */ },
        )
    }

    selectedReport?.let { report ->
        IssueDetailDialog(
            state = issueDetailState,
            issueNumber = report.number,
            issueTitle = report.title,
            onDismiss = { feedbackViewModel.closeIssueDetail() },
            onRefresh = { feedbackViewModel.fetchIssueDetail(report.number) },
            onReplyTextChange = { feedbackViewModel.updateReplyText(it) },
            onReplyImageSelected = { feedbackViewModel.setReplyImage(it) },
            onPostComment = { feedbackViewModel.postComment() },
        )
    }
}

@Composable
private fun ReportRow(
    report: BugReport,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${report.number}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = report.createdAt.take(10),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceFaint,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val statusColor = if (report.status == "closed") Error else Success
            Card(
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
            ) {
                Text(
                    text = report.status.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
