package dev.cremin.mergejo.domain.model

import kotlinx.datetime.Instant

/** A comment on a pull request, split into [blocks] for display. */
data class PrComment(
    val id: Long,
    val author: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val blocks: List<CommentBlock>,
) {
    /** CI bots often keep one comment and rewrite it on each push. */
    val edited: Boolean
        get() = createdAt != null && updatedAt != null && updatedAt > createdAt
}

sealed interface CommentBlock {
    /** Markdown outside any `<details>` block. */
    data class Prose(val markdown: String) : CommentBlock

    /**
     * A top-level `<details>` block. [summary] is plain text, with `**` around the parts the HTML
     * made bold. When the body is a single fenced code block (command output such as a closure
     * diff), [code] holds its text and [markdown] is empty, so it can be shown unwrapped.
     */
    data class Details(val summary: String, val markdown: String, val code: String?) : CommentBlock
}
