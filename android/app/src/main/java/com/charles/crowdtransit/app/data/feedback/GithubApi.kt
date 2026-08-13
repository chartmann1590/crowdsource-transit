package com.charles.crowdtransit.app.data.feedback

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Talks to the ors-proxy Worker's feedback relay endpoints, not api.github.com directly —
 * the Worker holds the GitHub token as a server-side secret and hardcodes this app's own
 * repo, so no owner/repo/credential ever needs to travel through this app. See
 * NetworkModule.provideGithubApi and workers/ors-proxy/src/index.ts.
 */
interface GithubApi {

    @POST("feedback/issue")
    suspend fun createIssue(@Body request: CreateIssueRequest): Response<GithubIssue>

    @GET("feedback/issue/{issueNumber}")
    suspend fun getIssue(@Path("issueNumber") issueNumber: Int): Response<GithubIssue>

    @GET("feedback/issue/{issueNumber}/comments")
    suspend fun getComments(@Path("issueNumber") issueNumber: Int): Response<List<GithubComment>>

    @POST("feedback/issue/{issueNumber}/comments")
    suspend fun postComment(
        @Path("issueNumber") issueNumber: Int,
        @Body request: PostCommentRequest,
    ): Response<GithubComment>

    @POST("feedback/upload-image")
    suspend fun uploadAsset(@Body request: UploadImageRequest): Response<UploadAssetResponse>
}
