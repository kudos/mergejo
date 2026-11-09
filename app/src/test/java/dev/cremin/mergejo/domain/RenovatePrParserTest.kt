package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.UpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenovatePrParserTest {

    @Test
    fun `minor bump from title and change table`() {
        val parsed = RenovatePrParser.parse(
            title = "Update dependency react to v18.3.1",
            body = "| Package | Change |\n|---|---|\n| react | `18.2.0` -> `18.3.1` |",
        )
        assertEquals("react", parsed.dependency)
        assertEquals("18.2.0", parsed.currentVersion)
        assertEquals("18.3.1", parsed.newVersion)
        assertEquals(UpdateType.MINOR, parsed.updateType)
        assertFalse(parsed.isSecurity)
    }

    @Test
    fun `major bump`() {
        val parsed = RenovatePrParser.parse(
            title = "Update dependency foo to v3.0.0",
            body = "Change: `2.5.1` -> `3.0.0`",
        )
        assertEquals(UpdateType.MAJOR, parsed.updateType)
        assertEquals("2.5.1", parsed.currentVersion)
        assertEquals("3.0.0", parsed.newVersion)
    }

    @Test
    fun `patch bump`() {
        val parsed = RenovatePrParser.parse(
            title = "Update dependency bar to v1.2.4",
            body = "`1.2.3` -> `1.2.4`",
        )
        assertEquals(UpdateType.PATCH, parsed.updateType)
    }

    @Test
    fun `security update detected from CVE in body`() {
        val parsed = RenovatePrParser.parse(
            title = "Update dependency baz to v1.0.1",
            body = "`1.0.0` -> `1.0.1`\n\nThis release fixes CVE-2026-1234.",
        )
        assertTrue(parsed.isSecurity)
        assertEquals(UpdateType.PATCH, parsed.updateType)
    }

    @Test
    fun `digest update from parenthetical`() {
        val parsed = RenovatePrParser.parse(
            title = "Update actions/checkout digest to a1b2c3d (digest)",
            body = "",
        )
        assertEquals("actions/checkout", parsed.dependency)
        assertEquals(UpdateType.DIGEST, parsed.updateType)
    }

    @Test
    fun `no version info yields OTHER and null fields`() {
        val parsed = RenovatePrParser.parse(title = "Update something", body = "")
        assertNull(parsed.newVersion)
        assertEquals(UpdateType.OTHER, parsed.updateType)
    }

    @Test
    fun `explicit minor parenthetical wins even without change table`() {
        val parsed = RenovatePrParser.parse(
            title = "Update foo monorepo to v2.1.0 (minor)",
            body = "",
        )
        assertEquals("foo", parsed.dependency)
        assertEquals("2.1.0", parsed.newVersion)
        assertEquals(UpdateType.MINOR, parsed.updateType)
    }

    // --- Real Renovate output (captured from cremin.dev): Unicode `→` arrow + Update column. ---

    @Test
    fun `real renovate table with unicode arrow and update column`() {
        val body = """
            This PR contains the following updates:

            | Package | Type | Update | Change | Pending |
            |---|---|---|---|---|
            | [hono](https://hono.dev) | dependencies | patch | [`4.12.27` → `4.12.28`](https://x) | `4.12.30` (+1) |

            ---

            ### Release Notes
        """.trimIndent()
        val parsed = RenovatePrParser.parse("Update dependency hono to v4.12.28", body)
        assertEquals("hono", parsed.dependency)
        assertEquals("4.12.27", parsed.currentVersion)
        assertEquals("4.12.28", parsed.newVersion)
        assertEquals(UpdateType.PATCH, parsed.updateType)
    }

    @Test
    fun `range-prefixed minor bump from update column`() {
        val body = """
            | Package | Type | Update | Change |
            |---|---|---|---|
            | [@opentelemetry/auto-instrumentations-node](https://x) | dependencies | minor | [`^0.77.0` → `^0.78.0`](https://y) |

            ---
        """.trimIndent()
        val parsed = RenovatePrParser.parse(
            "Update dependency @opentelemetry/auto-instrumentations-node to ^0.78.0",
            body,
        )
        assertEquals("0.77.0", parsed.currentVersion)
        assertEquals("0.78.0", parsed.newVersion)
        assertEquals(UpdateType.MINOR, parsed.updateType)
    }

    @Test
    fun `single-segment bump is major via semver fallback`() {
        // No explicit Update column — falls through to component comparison.
        val parsed = RenovatePrParser.parse(
            title = "Update actions/checkout action to v5",
            body = "| Package | Change |\n|---|---|\n| checkout | [`v4` → `v5`](https://x) |",
        )
        assertEquals("4", parsed.currentVersion)
        assertEquals("5", parsed.newVersion)
        assertEquals(UpdateType.MAJOR, parsed.updateType)
    }

    @Test
    fun `arrow in release notes does not override the table`() {
        val body = """
            | Package | Type | Update | Change |
            |---|---|---|---|
            | webpack | devDependencies | patch | [`5.108.1` → `5.108.4`](https://x) |

            ---

            ### Release Notes
            Migrated the config: `oldApi` -> `9.9.9` in this release.
        """.trimIndent()
        val parsed = RenovatePrParser.parse("Update dependency webpack to v5.108.4", body)
        assertEquals("5.108.1", parsed.currentVersion)
        assertEquals("5.108.4", parsed.newVersion)
        assertEquals(UpdateType.PATCH, parsed.updateType)
    }

    @Test
    fun `grouped PR takes the most significant row and keeps the structured body`() {
        val body = """
            | Package | Type | Update | Change |
            |---|---|---|---|
            | [vitest](https://x) | devDependencies | patch | [`3.1.0` → `3.1.1`](https://y) |
            | [@vitest/coverage-v8](https://x) | devDependencies | major | [`2.1.9` → `3.1.1`](https://y) |

            ---
        """.trimIndent()
        val parsed = RenovatePrParser.parse("Update vitest monorepo to v3.1.1", body)
        assertEquals("vitest", parsed.dependency)
        assertEquals(UpdateType.MAJOR, parsed.updateType)
        assertEquals(2, parsed.body.updates.size)
        assertEquals("3.1.0", parsed.currentVersion)
    }

    @Test
    fun `security suffix in title marks the PR as security`() {
        val parsed = RenovatePrParser.parse("Update dependency lodash to v4.17.21 [SECURITY]", "")
        assertTrue(parsed.isSecurity)
    }

    @Test
    fun `dependency falls back to the table when the title is unrecognised`() {
        val parsed = RenovatePrParser.parse(
            "chore(deps): bump things",
            "| Package | Change |\n|---|---|\n| [left-pad](https://x) | `1.0.0` → `1.1.0` |",
        )
        assertEquals("left-pad", parsed.dependency)
        assertEquals(UpdateType.MINOR, parsed.updateType)
    }

    @Test
    fun `lock file maintenance has no dependency`() {
        val body = checkNotNull(javaClass.getResource("/renovate/lock-file-maintenance.md")).readText()
        val parsed = RenovatePrParser.parse("Lock file maintenance", body)
        // So the list and the top bar show the PR title rather than the update type twice.
        assertNull(parsed.dependency)
        assertEquals("lockFileMaintenance", parsed.body.updates.single().updateLabel)
    }
}
