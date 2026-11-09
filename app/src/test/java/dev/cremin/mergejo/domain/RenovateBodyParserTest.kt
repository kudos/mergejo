package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.Notice
import dev.cremin.mergejo.domain.model.ScheduleEntry
import dev.cremin.mergejo.domain.model.UpdateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenovateBodyParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/renovate/$name")) { "missing fixture $name" }.readText()

    // --- Real body captured from cremin.dev (jonathan/combine.fm) ---

    @Test
    fun `real body - table row`() {
        val body = RenovateBodyParser.parse(fixture("koa-compress.md"))
        val update = body.updates.single()
        assertEquals("koa-compress", update.name)
        assertEquals("https://github.com/koajs/compress", update.url)
        assertEquals("dependencies", update.depType)
        assertEquals(UpdateType.MINOR, update.updateType)
        assertEquals("~5.0.1", update.from)
        assertEquals("~5.1.0", update.to)
        assertEquals("https://renovatebot.com/diffs/npm/koa-compress/5.0.1/5.1.1", update.diffUrl)
        assertNull(update.pending)
    }

    @Test
    fun `real body - release notes split per release`() {
        val body = RenovateBodyParser.parse(fixture("koa-compress.md"))
        val pkg = body.releaseNotes.single()
        assertEquals("koajs/compress (koa-compress)", pkg.title)
        assertEquals(listOf("v5.1.1", "v5.1.0"), pkg.releases.map { it.version })

        val latest = pkg.releases.first()
        assertEquals("https://github.com/koajs/compress/releases/tag/v5.1.1", latest.url)
        assertEquals("fix brotli options", latest.title)
        assertEquals("https://github.com/koajs/compress/compare/v5.1.0...v5.1.1", latest.compareUrl)
        // Zero-width anti-mention entity removed; compare link lifted out of the notes.
        assertTrue(latest.notesMarkdown.contains("[@patrickhulce]"))
        assertFalse(latest.notesMarkdown.contains("Compare Source"))
        assertFalse(latest.notesMarkdown.contains("</details>"))
    }

    @Test
    fun `real body - configuration and no boilerplate notices`() {
        val body = RenovateBodyParser.parse(fixture("koa-compress.md"))
        assertEquals(false, body.config.automerge)
        assertFalse(body.config.immortal)
        assertEquals(
            listOf(ScheduleEntry("Branch creation", listOf("At any time")), ScheduleEntry("Automerge", listOf("At any time"))),
            body.config.schedule,
        )
        assertNull(body.config.timezone)
        assertTrue(body.notices.isEmpty())
        assertTrue(body.advisories.isEmpty())
        assertTrue(body.isStructured)
    }

    // --- Full-template fixture: grouped, Pending column, warnings, vulnerabilities, nesting ---

    @Test
    fun `grouped table keeps every row with raw versions and pending`() {
        val updates = RenovateBodyParser.parse(fixture("grouped-security.md")).updates
        assertEquals(listOf("hono", "@hono/node-server"), updates.map { it.name })
        assertEquals("https://github.com/honojs/hono", updates[0].sourceUrl)
        assertEquals("4.12.30 (+1)", updates[0].pending)
        assertEquals(UpdateType.MAJOR, updates[1].updateType)
        assertEquals("^1.14.0", updates[1].from)
        assertEquals("^2.0.0", updates[1].to)
        assertNull(updates[1].pending)
    }

    @Test
    fun `notices are classified`() {
        val notices = RenovateBodyParser.parse(fixture("grouped-security.md")).notices
        assertEquals(listOf(Notice.Kind.WARNING, Notice.Kind.SECURITY), notices.map { it.kind })
        assertTrue(notices[1].markdown.contains("CSRF middleware"))
    }

    @Test
    fun `advisories keep their links and de-duplicate`() {
        val advisories = RenovateBodyParser.parse(fixture("grouped-security.md")).advisories
        assertEquals(listOf("CVE-2026-1234", "GHSA-abcd-efgh-ijkl"), advisories.map { it.id })
        assertEquals("https://github.com/advisories/GHSA-abcd-efgh-ijkl", advisories[1].url)
    }

    @Test
    fun `release notes survive nested details and rules inside changelogs`() {
        val notes = RenovateBodyParser.parse(fixture("grouped-security.md")).releaseNotes
        assertEquals(2, notes.size)
        assertEquals("honojs/node-server (@hono/node-server)", notes[1].title)

        val hono = notes[0].releases.single()
        assertTrue(hono.notesMarkdown.contains("🐛 Bug Fix"))
        assertTrue(hono.notesMarkdown.contains("nested details content"))

        val node = notes[1].releases
        assertEquals(listOf("v2.0.0", "v1.15.0"), node.map { it.version })
        assertEquals("Node 20+", node[0].title)
        assertTrue(node[0].notesMarkdown.contains("More text after a rule"))
    }

    @Test
    fun `configuration flags`() {
        val config = RenovateBodyParser.parse(fixture("grouped-security.md")).config
        assertEquals(true, config.automerge)
        assertTrue(config.immortal)
        assertTrue(config.rebasing!!.startsWith("Whenever PR is behind"))
    }

    // --- Real body from cremin.dev (jonathan/nixos), Renovate 44: the schedule as a list ---

    @Test
    fun `real body - schedule list with timezone`() {
        val config = RenovateBodyParser.parse(fixture("lock-file-maintenance.md")).config
        assertEquals(
            listOf(
                ScheduleEntry("Branch creation", listOf("before 4am on monday")),
                ScheduleEntry("Automerge", listOf("At any time")),
            ),
            config.schedule,
        )
        assertEquals("UTC", config.timezone)
        assertEquals(false, config.automerge)
        assertTrue(config.immortal)
        assertTrue(config.rebasing!!.startsWith("Whenever PR becomes conflicted"))
    }

    @Test
    fun `schedule list with several times`() {
        val config = RenovateBodyParser.parse(
            """
            ### Configuration

            📅 **Schedule**: (Europe/London)

            - Branch creation
              - "after 10pm every weekday"
              - "before 5am every weekday"
            - Automerge
              - At any time (no schedule defined)

            🚦 **Automerge**: Disabled by config.
            """.trimIndent(),
        ).config
        assertEquals(
            listOf(
                ScheduleEntry("Branch creation", listOf("after 10pm every weekday", "before 5am every weekday")),
                ScheduleEntry("Automerge", listOf("At any time")),
            ),
            config.schedule,
        )
        assertEquals("Europe/London", config.timezone)
        assertEquals(false, config.automerge)
    }

    @Test
    fun `one-line schedule with timezone and commas`() {
        val config = RenovateBodyParser.parse(
            """
            ### Configuration

            📅 **Schedule**: Branch creation - "after 10pm every weekday,before 5am every weekday" in timezone Europe/Berlin, Automerge - At any time (no schedule defined).
            """.trimIndent(),
        ).config
        assertEquals(
            listOf(
                ScheduleEntry("Branch creation", listOf("after 10pm every weekday,before 5am every weekday")),
                ScheduleEntry("Automerge", listOf("At any time")),
            ),
            config.schedule,
        )
        assertEquals("Europe/Berlin", config.timezone)
    }

    // --- Shapes without a version pair ---

    @Test
    fun `lock file maintenance has no package column`() {
        val body = RenovateBodyParser.parse(
            """
            This PR contains the following updates:

            | Update | Change |
            |---|---|
            | lockFileMaintenance | All locks refreshed |
            """.trimIndent(),
        )
        val update = body.updates.single()
        assertEquals("", update.name)
        assertEquals("lockFileMaintenance", update.updateLabel)
        assertEquals(UpdateType.OTHER, update.updateType)
        assertNull(update.from)
        assertEquals("All locks refreshed", update.changeText)
    }

    @Test
    fun `digest change inferred without update column`() {
        val update = RenovateBodyParser.parse(
            "| Package | Change |\n|---|---|\n| node | `a1b2c3d` -> `e4f5a6b` |",
        ).updates.single()
        assertEquals(UpdateType.DIGEST, update.updateType)
    }

    @Test
    fun `date-style versions are not digests`() {
        assertEquals(UpdateType.MAJOR, RenovateBodyParser.inferUpdateType("20240101", "20250101"))
    }

    @Test
    fun `non-renovate body is unstructured`() {
        assertFalse(RenovateBodyParser.parse("Just a description.").isStructured)
    }
}
