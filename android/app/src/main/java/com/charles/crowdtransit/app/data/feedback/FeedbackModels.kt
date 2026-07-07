package com.charles.crowdtransit.app.data.feedback

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BugReport(
    val number: Int,
    val title: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "html_url") val htmlUrl: String,
)

@JsonClass(generateAdapter = true)
data class CreateIssueRequest(
    val title: String,
    val body: String,
)

@JsonClass(generateAdapter = true)
data class GithubIssue(
    val number: Int,
    val title: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    val body: String? = null,
)

@JsonClass(generateAdapter = true)
data class GithubUser(
    val login: String,
)

@JsonClass(generateAdapter = true)
data class GithubComment(
    val id: Long,
    val body: String,
    @Json(name = "created_at") val createdAt: String,
    val user: GithubUser,
)

@JsonClass(generateAdapter = true)
data class PostCommentRequest(
    val body: String,
)

@JsonClass(generateAdapter = true)
data class UploadAssetRequest(
    val message: String,
    val content: String,
)

@JsonClass(generateAdapter = true)
data class UploadContentInfo(
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class UploadAssetResponse(
    val content: UploadContentInfo? = null,
)
