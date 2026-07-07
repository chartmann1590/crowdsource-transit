package com.charles.crowdtransit.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.crowdtransit.app.BuildConfig
import com.charles.crowdtransit.app.data.feedback.BugReport
import com.charles.crowdtransit.app.data.feedback.BugReportRepo
import com.charles.crowdtransit.app.data.feedback.CreateIssueRequest
import com.charles.crowdtransit.app.data.feedback.DiagnosticsHelper
import com.charles.crowdtransit.app.data.feedback.GithubApi
import com.charles.crowdtransit.app.data.feedback.GithubComment
import com.charles.crowdtransit.app.data.feedback.GithubIssue
import com.charles.crowdtransit.app.data.feedback.PostCommentRequest
import com.charles.crowdtransit.app.data.feedback.UploadAssetRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ReportDialogState(
    val title: String = "",
    val description: String = "",
    val includeDiagnostics: Boolean = true,
    val name: String = "",
    val email: String = "",
    val imageUri: Uri? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

data class IssueDetailState(
    val issue: GithubIssue? = null,
    val comments: List<GithubComment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val replyText: String = "",
    val replyImageUri: Uri? = null,
    val isPostingComment: Boolean = false,
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val githubApi: GithubApi,
    private val bugReportRepo: BugReportRepo,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val bugReports: StateFlow<List<BugReport>> = bugReportRepo.bugReports
        .let { flow ->
            val state = MutableStateFlow<List<BugReport>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state
        }

    private val _reportState = MutableStateFlow(ReportDialogState())
    val reportState: StateFlow<ReportDialogState> = _reportState.asStateFlow()

    private val _showReportDialog = MutableStateFlow(false)
    val showReportDialog: StateFlow<Boolean> = _showReportDialog.asStateFlow()

    private val _issueDetailState = MutableStateFlow(IssueDetailState())
    val issueDetailState: StateFlow<IssueDetailState> = _issueDetailState.asStateFlow()

    private val _selectedReport = MutableStateFlow<BugReport?>(null)
    val selectedReport: StateFlow<BugReport?> = _selectedReport.asStateFlow()

    val isConfigured: Boolean
        get() = BuildConfig.GITHUB_API_TOKEN.isNotEmpty() &&
                BuildConfig.GITHUB_REPO_OWNER.isNotEmpty() &&
                BuildConfig.GITHUB_REPO_NAME.isNotEmpty()

    fun openReportDialog() {
        _reportState.value = ReportDialogState()
        _showReportDialog.value = true
    }

    fun closeReportDialog() {
        _showReportDialog.value = false
    }

    fun updateReportTitle(title: String) {
        _reportState.value = _reportState.value.copy(title = title, error = null)
    }

    fun updateReportDescription(description: String) {
        _reportState.value = _reportState.value.copy(description = description, error = null)
    }

    fun updateReportIncludeDiagnostics(include: Boolean) {
        _reportState.value = _reportState.value.copy(includeDiagnostics = include)
    }

    fun updateReportName(name: String) {
        _reportState.value = _reportState.value.copy(name = name)
    }

    fun updateReportEmail(email: String) {
        _reportState.value = _reportState.value.copy(email = email)
    }

    fun setReportImage(uri: Uri?) {
        _reportState.value = _reportState.value.copy(imageUri = uri)
    }

    fun submitReport() {
        val state = _reportState.value
        if (state.title.isBlank()) {
            _reportState.value = state.copy(error = "Title is required")
            return
        }
        if (state.description.isBlank()) {
            _reportState.value = state.copy(error = "Description is required")
            return
        }
        if (!isConfigured) {
            _reportState.value = state.copy(error = "GitHub repository is not configured. Check local.properties.")
            return
        }

        _reportState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                var body = buildString {
                    appendLine("## Description")
                    appendLine()
                    appendLine(state.description)
                    appendLine()
                    appendLine("## Contact Info")
                    appendLine()
                    appendLine("- Name: ${state.name.ifBlank { "Not provided" }}")
                    appendLine("- Email: ${state.email.ifBlank { "Not provided" }}")
                }

                if (state.imageUri != null) {
                    val imageUrl = uploadImage(state.imageUri)
                    if (imageUrl != null) {
                        body += "\n\n## Attachment\n\n![Screenshot]($imageUrl)\n"
                    }
                }

                if (state.includeDiagnostics) {
                    body += "\n\n${DiagnosticsHelper.collect(context)}\n"
                }

                val owner = BuildConfig.GITHUB_REPO_OWNER
                val repo = BuildConfig.GITHUB_REPO_NAME
                val issueTitle = "[Feedback] ${state.title}"
                val request = CreateIssueRequest(title = issueTitle, body = body)

                val response = githubApi.createIssue(owner, repo, request)
                if (response.isSuccessful) {
                    val issue = response.body()!!
                    val report = BugReport(
                        number = issue.number,
                        title = issue.title,
                        status = issue.state,
                        createdAt = issue.createdAt,
                        htmlUrl = issue.htmlUrl,
                    )
                    bugReportRepo.saveBugReport(report)
                    _reportState.value = ReportDialogState(success = true)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _reportState.value = _reportState.value.copy(
                        isSubmitting = false,
                        error = "Failed to create issue: ${response.code()}. $errorBody",
                    )
                }
            } catch (e: Exception) {
                _reportState.value = _reportState.value.copy(
                    isSubmitting = false,
                    error = "Error: ${e.message}",
                )
            }
        }
    }

    fun dismissSuccess() {
        _reportState.value = ReportDialogState()
        _showReportDialog.value = false
    }

    fun openIssueDetail(report: BugReport) {
        _selectedReport.value = report
        _issueDetailState.value = IssueDetailState(isLoading = true)
        fetchIssueDetail(report.number)
    }

    fun closeIssueDetail() {
        _selectedReport.value = null
        _issueDetailState.value = IssueDetailState()
    }

    fun fetchIssueDetail(issueNumber: Int) {
        _issueDetailState.value = _issueDetailState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val owner = BuildConfig.GITHUB_REPO_OWNER
                val repo = BuildConfig.GITHUB_REPO_NAME

                val issueResponse = githubApi.getIssue(owner, repo, issueNumber)
                val commentsResponse = githubApi.getComments(owner, repo, issueNumber)

                if (issueResponse.isSuccessful) {
                    val issue = issueResponse.body()!!
                    _issueDetailState.value = _issueDetailState.value.copy(
                        issue = issue,
                        isLoading = false,
                    )
                    val report = BugReport(
                        number = issue.number,
                        title = issue.title,
                        status = issue.state,
                        createdAt = issue.createdAt,
                        htmlUrl = issue.htmlUrl,
                    )
                    bugReportRepo.saveBugReport(report)
                } else {
                    _issueDetailState.value = _issueDetailState.value.copy(
                        isLoading = false,
                        error = "Failed to load issue: ${issueResponse.code()}",
                    )
                }

                if (commentsResponse.isSuccessful) {
                    _issueDetailState.value = _issueDetailState.value.copy(
                        comments = commentsResponse.body() ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                _issueDetailState.value = _issueDetailState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message}",
                )
            }
        }
    }

    fun updateReplyText(text: String) {
        _issueDetailState.value = _issueDetailState.value.copy(replyText = text)
    }

    fun setReplyImage(uri: Uri?) {
        _issueDetailState.value = _issueDetailState.value.copy(replyImageUri = uri)
    }

    fun postComment() {
        val state = _issueDetailState.value
        val issue = state.issue ?: return

        if (state.replyText.isBlank() && state.replyImageUri == null) {
            _issueDetailState.value = state.copy(error = "Comment cannot be empty")
            return
        }

        val issueNumber = issue.number
        _issueDetailState.value = state.copy(isPostingComment = true, error = null)

        viewModelScope.launch {
            try {
                var body = "## Reply\n\n${state.replyText}".trimEnd()
                if (state.replyImageUri != null) {
                    val imageUrl = uploadImage(state.replyImageUri)
                    if (imageUrl != null) {
                        body += "\n\n## Attachment\n\n![Screenshot]($imageUrl)\n"
                    } else {
                        _issueDetailState.value = _issueDetailState.value.copy(
                            isPostingComment = false,
                            error = "Image upload failed. Comment was not posted.",
                        )
                        return@launch
                    }
                }

                val owner = BuildConfig.GITHUB_REPO_OWNER
                val repo = BuildConfig.GITHUB_REPO_NAME
                val request = PostCommentRequest(body = body)

                val response = githubApi.postComment(owner, repo, issueNumber, request)
                if (response.isSuccessful) {
                    _issueDetailState.value = _issueDetailState.value.copy(
                        replyText = "",
                        replyImageUri = null,
                        isPostingComment = false,
                    )
                    fetchIssueDetail(issueNumber)
                } else {
                    _issueDetailState.value = _issueDetailState.value.copy(
                        isPostingComment = false,
                        error = "Failed to post comment: ${response.code()}",
                    )
                }
            } catch (e: Exception) {
                _issueDetailState.value = _issueDetailState.value.copy(
                    isPostingComment = false,
                    error = "Error: ${e.message}",
                )
            }
        }
    }

    private suspend fun uploadImage(uri: Uri): String? {
        val base64 = uriToBase64(context, uri) ?: return null
        val dateStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val random = UUID.randomUUID().toString().take(8)
        val filename = "issue-$dateStr-$random.png"
        val path = "${BuildConfig.FEEDBACK_ASSETS_DIR}/$filename"

        val owner = BuildConfig.GITHUB_REPO_OWNER
        val repo = BuildConfig.GITHUB_REPO_NAME
        val request = UploadAssetRequest(
            message = "Upload $filename",
            content = base64,
        )

        val response = githubApi.uploadAsset(owner, repo, path, request)
        return if (response.isSuccessful) {
            response.body()?.content?.downloadUrl
        } else {
            null
        }
    }

    companion object {
        fun uriToBase64(context: Context, uri: Uri): String? {
            return try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
