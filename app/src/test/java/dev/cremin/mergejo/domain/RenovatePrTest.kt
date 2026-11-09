package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.CiStatus
import dev.cremin.mergejo.domain.model.RenovatePr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenovatePrTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/renovate/$name")) { "missing fixture $name" }.readText()

    private fun pr(title: String, body: String): RenovatePr {
        val parsed = RenovatePrParser.parse(title, body)
        return RenovatePr(
            repoOwner = "jonathan", repoName = "app", number = 1, title = title, htmlUrl = "",
            branch = "renovate/x", baseBranch = "main", headSha = "", author = "renovate", createdAt = null,
            dependency = parsed.dependency, currentVersion = parsed.currentVersion, newVersion = parsed.newVersion,
            updateType = parsed.updateType, isSecurity = parsed.isSecurity, ciStatus = CiStatus.NONE,
            checks = emptyList(), mergeable = null, labels = emptyList(), bodyMarkdown = body, body = parsed.body,
            rebaseRequested = false,
        )
    }

    @Test
    fun `lock file maintenance is summarised by its change`() {
        val pr = pr("Lock file maintenance", fixture("lock-file-maintenance.md"))
        assertEquals("All locks refreshed", pr.changeSummary)
        assertEquals("lockFileMaintenance", pr.updateLabel)
        assertNull(pr.versionChange)
    }

    @Test
    fun `a package update has no change summary`() {
        val pr = pr("Update dependency koa-compress to ~5.1.0", fixture("koa-compress.md"))
        assertNull(pr.changeSummary)
        assertEquals("~5.0.1 → ~5.1.0", pr.versionChange)
    }
}
