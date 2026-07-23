package dev.cremin.mergejo.domain.model

import kotlinx.datetime.Instant

/** A single CI check attached to a PR head commit. */
data class CheckRun(
    val context: String,
    val description: String,
    val status: CiStatus,
    val targetUrl: String?,
)

/**
 * A Renovate pull request, enriched with parsed dependency metadata and CI state.
 * Carries everything both the list and the detail screen need (there are only a
 * handful open at once, so keeping the body + checks in-memory is cheap).
 */
data class RenovatePr(
    val repoOwner: String,
    val repoName: String,
    val number: Long,
    val title: String,
    val htmlUrl: String,
    val branch: String,
    val baseBranch: String,
    val headSha: String,
    val author: String,
    val createdAt: Instant?,
    val dependency: String?,
    val currentVersion: String?,
    val newVersion: String?,
    val updateType: UpdateType,
    val isSecurity: Boolean,
    val ciStatus: CiStatus,
    val checks: List<CheckRun>,
    val mergeable: Boolean?,
    val labels: List<String>,
    val bodyMarkdown: String,
    val rebaseRequested: Boolean,
) {
    val repoSlug: String get() = "$repoOwner/$repoName"

    /** Stable identity used for navigation and list keys. */
    val id: String get() = "$repoOwner/$repoName#$number"

    val versionChange: String?
        get() = when {
            currentVersion != null && newVersion != null -> "$currentVersion → $newVersion"
            else -> newVersion
        }

    companion object {
        /** Parses a navigation id ("owner/repo#number") back into its parts. */
        fun parseId(id: String): Triple<String, String, Long>? {
            val hash = id.lastIndexOf('#')
            val slash = id.indexOf('/')
            if (hash <= 0 || slash <= 0 || slash >= hash) return null
            val owner = id.substring(0, slash)
            val repo = id.substring(slash + 1, hash)
            val number = id.substring(hash + 1).toLongOrNull() ?: return null
            return Triple(owner, repo, number)
        }
    }
}
