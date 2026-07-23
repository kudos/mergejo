package dev.cremin.mergejo.domain.model

/** Aggregated CI state for a PR head commit. */
enum class CiStatus {
    SUCCESS,
    PENDING,
    FAILURE,
    NONE;

    companion object {
        /** Maps a Gitea/Forgejo commit-status state string to a [CiStatus]. */
        fun fromGiteaState(state: String): CiStatus = when (state.lowercase()) {
            "success" -> SUCCESS
            "pending" -> PENDING
            "warning" -> PENDING
            "failure", "error" -> FAILURE
            else -> NONE
        }
    }
}
