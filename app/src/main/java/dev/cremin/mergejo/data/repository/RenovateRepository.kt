package dev.cremin.mergejo.data.repository

import dev.cremin.mergejo.data.remote.api.ForgejoApi
import dev.cremin.mergejo.data.remote.dto.CommentDto
import dev.cremin.mergejo.data.remote.dto.CreatePullReviewOptions
import dev.cremin.mergejo.data.remote.dto.EditIssueOption
import dev.cremin.mergejo.data.remote.dto.MergePullRequestOption
import dev.cremin.mergejo.data.remote.dto.PullRequestDto
import dev.cremin.mergejo.data.remote.dto.RepoDto
import dev.cremin.mergejo.domain.CommentParser
import dev.cremin.mergejo.domain.RebaseCheckbox
import dev.cremin.mergejo.domain.RenovatePrParser
import dev.cremin.mergejo.domain.model.CheckRun
import dev.cremin.mergejo.domain.model.CiStatus
import dev.cremin.mergejo.domain.model.MergeStyle
import dev.cremin.mergejo.domain.model.PrComment
import dev.cremin.mergejo.domain.model.RenovatePr
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import retrofit2.HttpException
import retrofit2.Response

/**
 * The single seam between the Forgejo API and the UI. Aggregates open Renovate
 * PRs across every repo the signed-in user can see (identified by the
 * `renovate/` head-branch prefix), enriches each with parsed metadata and CI
 * status, and exposes reactive state plus the per-PR actions.
 */
@Singleton
class RenovateRepository @Inject constructor(
    private val api: ForgejoApi,
) {
    private val _pullRequests = MutableStateFlow<List<RenovatePr>>(emptyList())
    val pullRequests: StateFlow<List<RenovatePr>> = _pullRequests.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun getById(id: String): RenovatePr? = _pullRequests.value.firstOrNull { it.id == id }

    /**
     * Reloads every open Renovate PR into [pullRequests]. Also returns the outcome, for
     * callers outside the UI (the background poll) that need to know whether it worked.
     */
    suspend fun refresh(): Result<List<RenovatePr>> {
        _isRefreshing.value = true
        _error.value = null
        return try {
            val prs = coroutineScope {
                val repos = allPages { page -> api.listUserRepos(page = page) }.filterNot { it.archived }
                val gate = Semaphore(MAX_CONCURRENCY)

                val renovatePrs = repos.map { repo ->
                    async {
                        gate.withPermit {
                            val owner = repo.ownerLogin()
                            runCatching { allPages { page -> api.listPulls(owner, repo.name, state = "open", page = page) } }
                                .getOrDefault(emptyList())
                                .filter { it.head.ref.startsWith(RENOVATE_BRANCH_PREFIX) }
                                .map { repo to it }
                        }
                    }
                }.awaitAll().flatten()

                renovatePrs.map { (repo, dto) ->
                    async { gate.withPermit { buildPr(repo, dto) } }
                }.awaitAll()
            }
            val sorted = prs.sortedWith(
                compareByDescending<RenovatePr> { it.isSecurity }
                    .thenByDescending { it.createdAt },
            )
            _pullRequests.value = sorted
            Result.success(sorted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
            Result.failure(e)
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun merge(pr: RenovatePr, style: MergeStyle): Result<Unit> =
        api.mergePull(pr.repoOwner, pr.repoName, pr.number, MergePullRequestOption(style.giteaDo))
            .asResult()
            .onSuccess { remove(pr.id) }

    suspend fun approve(pr: RenovatePr): Result<Unit> =
        api.createReview(pr.repoOwner, pr.repoName, pr.number, CreatePullReviewOptions(event = "APPROVED"))
            .asResult()

    suspend fun close(pr: RenovatePr): Result<Unit> =
        api.editIssue(pr.repoOwner, pr.repoName, pr.number, EditIssueOption(state = "closed"))
            .asResult()
            .onSuccess { remove(pr.id) }

    /**
     * The PR's comments, oldest first. Not part of [refresh], which would cost a request per PR;
     * the detail screen loads them when it opens.
     */
    suspend fun comments(pr: RenovatePr): Result<List<PrComment>> = try {
        val dtos = api.listIssueComments(pr.repoOwner, pr.repoName, pr.number)
        val comments = withContext(Dispatchers.Default) { dtos.map { it.toPrComment() } }
        Result.success(comments.filter { it.blocks.isNotEmpty() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.toUserMessage(), e))
    }

    /** Ticks Renovate's rebase checkbox in the PR body; a no-op if already requested. */
    suspend fun rebase(pr: RenovatePr): Result<Unit> {
        val updatedBody = RebaseCheckbox.requestRebase(pr.bodyMarkdown)
            ?: return Result.success(Unit)
        return api.editIssue(pr.repoOwner, pr.repoName, pr.number, EditIssueOption(body = updatedBody))
            .asResult()
            .onSuccess { markRebaseRequested(pr.id) }
    }

    private suspend fun buildPr(repo: RepoDto, dto: PullRequestDto): RenovatePr {
        val owner = repo.ownerLogin()
        val combined = if (dto.head.sha.isNotBlank()) {
            runCatching { api.getCombinedStatus(owner, repo.name, dto.head.sha) }.getOrNull()
        } else {
            null
        }
        val checks = combined?.statuses.orEmpty().map {
            CheckRun(
                context = it.context,
                description = it.description,
                status = CiStatus.fromGiteaState(it.status),
                targetUrl = it.targetUrl.ifBlank { null }?.let { url -> resolveUrl(dto.htmlUrl, url) },
            )
        }
        val ciStatus = combined?.state?.takeIf { it.isNotBlank() }
            ?.let { CiStatus.fromGiteaState(it) }
            ?: CiStatus.NONE

        // Bodies can be hundreds of KB of changelog; keep the regex work off Main.
        val parsed = withContext(Dispatchers.Default) { RenovatePrParser.parse(dto.title, dto.body) }
        return RenovatePr(
            repoOwner = owner,
            repoName = repo.name,
            number = dto.number,
            title = dto.title,
            htmlUrl = dto.htmlUrl,
            branch = dto.head.ref,
            baseBranch = dto.base.ref,
            headSha = dto.head.sha,
            author = dto.user?.login.orEmpty(),
            createdAt = dto.createdAt.toInstantOrNull(),
            dependency = parsed.dependency,
            currentVersion = parsed.currentVersion,
            newVersion = parsed.newVersion,
            updateType = parsed.updateType,
            isSecurity = parsed.isSecurity,
            ciStatus = ciStatus,
            checks = checks,
            mergeable = dto.mergeable,
            labels = dto.labels.map { it.name },
            bodyMarkdown = dto.body,
            body = parsed.body,
            rebaseRequested = RebaseCheckbox.isChecked(dto.body),
        )
    }

    private fun CommentDto.toPrComment() = PrComment(
        id = id,
        author = user?.login.orEmpty(),
        createdAt = createdAt.toInstantOrNull(),
        updatedAt = updatedAt.toInstantOrNull(),
        blocks = CommentParser.parse(body),
    )

    /** Forgejo Actions reports a status's target as a path on the server (`/owner/repo/actions/runs/…`). */
    private fun resolveUrl(base: String, url: String): String? =
        runCatching { URI(base).resolve(url).toString() }.getOrNull()?.takeIf { it.startsWith("http") }

    private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun remove(id: String) {
        _pullRequests.value = _pullRequests.value.filterNot { it.id == id }
    }

    private fun markRebaseRequested(id: String) {
        _pullRequests.value = _pullRequests.value.map {
            if (it.id == id) it.copy(rebaseRequested = true) else it
        }
    }

    /**
     * Fetches every page of a Forgejo list endpoint. The server caps the page size
     * (`MAX_RESPONSE_ITEMS`, 50 by default) whatever `limit` asks for, so follow its
     * `Link: <…>; rel="next"` header instead of guessing from the page length.
     */
    private suspend fun <T> allPages(fetch: suspend (page: Int) -> Response<List<T>>): List<T> {
        val items = mutableListOf<T>()
        var page = 1
        while (true) {
            val response = fetch(page)
            if (!response.isSuccessful) throw HttpException(response)
            items += response.body().orEmpty()
            if (!response.hasNextPage() || page >= MAX_PAGES) return items
            page++
        }
    }

    private fun Response<*>.hasNextPage(): Boolean =
        headers().values("Link").any { it.contains("rel=\"next\"") }

    private fun RepoDto.ownerLogin(): String = owner?.login ?: fullName.substringBefore('/')

    private fun Response<*>.asResult(): Result<Unit> = if (isSuccessful) {
        body()?.let { (it as? okhttp3.ResponseBody)?.close() }
        Result.success(Unit)
    } else {
        val detail = runCatching { errorBody()?.string() }.getOrNull()?.take(300).orEmpty()
        Result.failure(IllegalStateException("HTTP ${code()}${if (detail.isBlank()) "" else ": $detail"}"))
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is HttpException -> if (code() == 401) "Not authorized — sign in again" else "HTTP ${code()}"
        is UnknownHostException -> "Can't reach the server"
        is IOException -> message ?: "Network error"
        else -> message ?: this::class.simpleName ?: "Unknown error"
    }

    private companion object {
        const val RENOVATE_BRANCH_PREFIX = "renovate/"
        const val MAX_CONCURRENCY = 6

        /** Safety stop for a server that keeps sending `rel="next"` (50 × 40 = 2000 items). */
        const val MAX_PAGES = 40
    }
}
