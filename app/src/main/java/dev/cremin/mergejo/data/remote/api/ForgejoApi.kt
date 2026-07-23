package dev.cremin.mergejo.data.remote.api

import dev.cremin.mergejo.data.remote.dto.CombinedStatusDto
import dev.cremin.mergejo.data.remote.dto.CreatePullReviewOptions
import dev.cremin.mergejo.data.remote.dto.EditIssueOption
import dev.cremin.mergejo.data.remote.dto.MergePullRequestOption
import dev.cremin.mergejo.data.remote.dto.PullRequestDto
import dev.cremin.mergejo.data.remote.dto.RepoDto
import dev.cremin.mergejo.data.remote.dto.UserDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gitea/Forgejo REST API (`/api/v1`). Auth is applied by an OkHttp interceptor,
 * so no token parameter is needed here. Paths are relative to the base URL
 * (FORGEJO_BASE_URL + "/"), which is why they start with `api/v1/...`.
 */
interface ForgejoApi {

    @GET("api/v1/user")
    suspend fun getAuthenticatedUser(): UserDto

    @GET("api/v1/user/repos")
    suspend fun listUserRepos(
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1,
    ): List<RepoDto>

    @GET("api/v1/repos/{owner}/{repo}/pulls")
    suspend fun listPulls(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1,
    ): List<PullRequestDto>

    @GET("api/v1/repos/{owner}/{repo}/pulls/{index}")
    suspend fun getPull(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("index") index: Long,
    ): PullRequestDto

    @GET("api/v1/repos/{owner}/{repo}/commits/{sha}/status")
    suspend fun getCombinedStatus(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String,
    ): CombinedStatusDto

    @POST("api/v1/repos/{owner}/{repo}/pulls/{index}/merge")
    suspend fun mergePull(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("index") index: Long,
        @Body body: MergePullRequestOption,
    ): Response<ResponseBody>

    @POST("api/v1/repos/{owner}/{repo}/pulls/{index}/reviews")
    suspend fun createReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("index") index: Long,
        @Body body: CreatePullReviewOptions,
    ): Response<ResponseBody>

    @PATCH("api/v1/repos/{owner}/{repo}/issues/{index}")
    suspend fun editIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("index") index: Long,
        @Body body: EditIssueOption,
    ): Response<ResponseBody>
}
