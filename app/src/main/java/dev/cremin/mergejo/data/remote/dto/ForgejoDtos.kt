package dev.cremin.mergejo.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gitea/Forgejo REST API DTOs. Property names are camelCase and mapped to the
 * API's snake_case via [kotlinx.serialization.json.JsonNamingStrategy.SnakeCase]
 * (configured on the shared Json). Only the fields we consume are declared;
 * unknown keys are ignored.
 */

@Serializable
data class UserDto(
    val id: Long = 0,
    val login: String = "",
    val fullName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
)

@Serializable
data class RepoDto(
    val id: Long = 0,
    val name: String = "",
    val fullName: String = "",
    val owner: UserDto? = null,
    val htmlUrl: String = "",
    val defaultBranch: String = "",
    val archived: Boolean = false,
    val allowMergeCommits: Boolean = true,
    val allowSquashMerge: Boolean = true,
    val allowRebase: Boolean = true,
    val defaultMergeStyle: String? = null,
)

@Serializable
data class LabelDto(
    val id: Long = 0,
    val name: String = "",
    val color: String = "",
)

@Serializable
data class PrBranchDto(
    val label: String = "",
    val ref: String = "",
    val sha: String = "",
)

@Serializable
data class PullRequestDto(
    val id: Long = 0,
    val number: Long = 0,
    val title: String = "",
    val body: String = "",
    val state: String = "",
    val htmlUrl: String = "",
    val user: UserDto? = null,
    val labels: List<LabelDto> = emptyList(),
    val head: PrBranchDto = PrBranchDto(),
    val base: PrBranchDto = PrBranchDto(),
    val mergeable: Boolean? = null,
    val merged: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CommitStatusDto(
    val status: String = "",
    val context: String = "",
    val description: String = "",
    val targetUrl: String = "",
)

@Serializable
data class CombinedStatusDto(
    val state: String = "",
    val sha: String = "",
    val totalCount: Int = 0,
    val statuses: List<CommitStatusDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val id: Long = 0,
    val body: String = "",
    val user: UserDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// --- Request bodies ---

@Serializable
data class MergePullRequestOption(
    // Gitea's field is literally "Do": merge | rebase | rebase-merge | squash | fast-forward-only
    @SerialName("Do") val doStyle: String,
    val deleteBranchAfterMerge: Boolean = true,
)

@Serializable
data class CreatePullReviewOptions(
    val event: String, // APPROVED | REQUEST_CHANGES | COMMENT | PENDING
    val body: String = "",
)

@Serializable
data class EditIssueOption(
    // Only the set (non-null) fields are sent (explicitNulls = false).
    val body: String? = null,
    val state: String? = null, // open | closed
)
