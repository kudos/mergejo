package dev.cremin.gitops.domain

/**
 * Renovate embeds a control checkbox in every PR body, anchored by a stable HTML
 * comment marker:
 *
 *   - [ ] <!-- rebase-check -->If you want to rebase/retry this PR, check this box 🔁
 *
 * Ticking it (editing the issue body to `- [x]`) tells Renovate to rebase/recreate
 * the branch on its next run.
 */
object RebaseCheckbox {

    private const val MARKER = "<!-- rebase-check -->"
    private const val UNCHECKED = "- [ ] $MARKER"
    private const val CHECKED = "- [x] $MARKER"

    fun hasCheckbox(body: String): Boolean = body.contains(MARKER)

    fun isChecked(body: String): Boolean = body.contains(CHECKED, ignoreCase = true)

    /**
     * Returns the body with the rebase checkbox ticked, or null when there is
     * nothing to do (no checkbox present, or it is already checked).
     */
    fun requestRebase(body: String): String? {
        if (!hasCheckbox(body)) return null
        if (isChecked(body)) return null
        val updated = body.replaceFirst(UNCHECKED, CHECKED)
        return updated.takeIf { it != body }
    }
}
