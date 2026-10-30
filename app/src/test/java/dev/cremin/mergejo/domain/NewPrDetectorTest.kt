package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.CiStatus
import dev.cremin.mergejo.domain.model.RenovateBody
import dev.cremin.mergejo.domain.model.RenovatePr
import dev.cremin.mergejo.domain.model.UpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPrDetectorTest {

    private fun pr(repo: String, number: Long) = RenovatePr(
        repoOwner = "jonathan",
        repoName = repo,
        number = number,
        title = "Update dependency x",
        htmlUrl = "",
        branch = "renovate/x",
        baseBranch = "main",
        headSha = "",
        author = "renovate",
        createdAt = null,
        dependency = null,
        currentVersion = null,
        newVersion = null,
        updateType = UpdateType.OTHER,
        isSecurity = false,
        ciStatus = CiStatus.NONE,
        checks = emptyList(),
        mergeable = null,
        labels = emptyList(),
        bodyMarkdown = "",
        body = RenovateBody(),
        rebaseRequested = false,
    )

    @Test
    fun `first poll records a baseline without announcing anything`() {
        val current = listOf(pr("a", 1), pr("b", 2))

        val outcome = NewPrDetector.detect(seen = null, current = current)

        assertTrue(outcome.newPrs.isEmpty())
        assertEquals(setOf("jonathan/a#1", "jonathan/b#2"), outcome.seen)
    }

    @Test
    fun `announces only PRs not seen before`() {
        val outcome = NewPrDetector.detect(
            seen = setOf("jonathan/a#1"),
            current = listOf(pr("a", 1), pr("a", 3)),
        )

        assertEquals(listOf("jonathan/a#3"), outcome.newPrs.map { it.id })
        assertEquals(setOf("jonathan/a#1", "jonathan/a#3"), outcome.seen)
    }

    @Test
    fun `a PR missing from one poll is not announced again when it reappears`() {
        val first = NewPrDetector.detect(seen = setOf("jonathan/a#1", "jonathan/b#2"), current = listOf(pr("a", 1)))
        val second = NewPrDetector.detect(seen = first.seen, current = listOf(pr("a", 1), pr("b", 2)))

        assertTrue(first.newPrs.isEmpty())
        assertTrue(second.newPrs.isEmpty())
    }

    @Test
    fun `seen set resets to the current ids once it outgrows the cap`() {
        val seen = (1..NewPrDetector.MAX_SEEN.toLong()).map { "jonathan/old#$it" }.toSet()

        val outcome = NewPrDetector.detect(seen = seen, current = listOf(pr("a", 1)))

        assertEquals(listOf("jonathan/a#1"), outcome.newPrs.map { it.id })
        assertEquals(setOf("jonathan/a#1"), outcome.seen)
    }
}
