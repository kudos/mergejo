package dev.cremin.mergejo.domain.model

/** Merge strategies supported by the Gitea/Forgejo merge endpoint (the `Do` field). */
enum class MergeStyle(val giteaDo: String) {
    MERGE("merge"),
    SQUASH("squash"),
    REBASE("rebase"),
}
