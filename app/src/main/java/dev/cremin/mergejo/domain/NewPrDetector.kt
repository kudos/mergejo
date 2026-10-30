package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.RenovatePr

/**
 * Decides which Renovate PRs are new since the last background poll.
 *
 * The seen set is the union of every PR id observed, rather than just the last poll's
 * results: a repo whose PR listing fails transiently drops out of one poll, and pruning
 * to the latest results would re-announce its PRs when it comes back. It's reset to the
 * current ids once it grows past [MAX_SEEN].
 */
object NewPrDetector {

    const val MAX_SEEN = 1000

    data class Outcome(val newPrs: List<RenovatePr>, val seen: Set<String>)

    /**
     * @param seen ids from earlier polls, or null when there's no baseline yet (first poll
     *   after notifications were turned on); a baseline poll records everything silently.
     */
    fun detect(seen: Set<String>?, current: List<RenovatePr>): Outcome {
        val currentIds = current.mapTo(LinkedHashSet()) { it.id }
        if (seen == null) return Outcome(emptyList(), currentIds)

        val newPrs = current.filter { it.id !in seen }
        val union = seen + currentIds
        return Outcome(newPrs, if (union.size > MAX_SEEN) currentIds else union)
    }
}
