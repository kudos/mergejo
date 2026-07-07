package dev.cremin.gitops.domain

import dev.cremin.gitops.domain.model.UpdateType
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
}
