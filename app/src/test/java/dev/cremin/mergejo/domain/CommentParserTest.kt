package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.CommentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/comments/$name")) { "missing fixture $name" }.readText()

    // --- Real comment from cremin.dev (jonathan/nixos#7, posted by forgejo-actions) ---

    @Test
    fun `closure diff - heading then one block per host`() {
        val blocks = CommentParser.parse(fixture("nixos-closure-diff.md"))

        assertEquals(CommentBlock.Prose("### Closure diff vs `14b7df47`"), blocks.first())
        val hosts = blocks.drop(1).map { it as CommentBlock.Details }
        assertEquals(
            listOf(
                "**desktop**: Closure size: 3470 -> 3469 (309 paths added, 310 paths removed, delta -1).",
                "**dev-01**: Closure size: 1390 -> 1390 (20 paths added, 20 paths removed, delta +0).",
                "**laptop**: Closure size: 3508 -> 3506 (356 paths added, 358 paths removed, delta -2).",
            ),
            hosts.map { it.summary },
        )
    }

    @Test
    fun `closure diff - output kept verbatim as code`() {
        val laptop = CommentParser.parse(fixture("nixos-closure-diff.md")).last() as CommentBlock.Details
        val code = checkNotNull(laptop.code)
        assertEquals("", laptop.markdown)

        val lines = code.lines()
        assertEquals("<<< /nix/store/5dzkwxksgxpzh22xva0j70nf8y4j4vn2-nixos-system-laptop-26.05.20261105.c56a87d", lines.first())
        assertEquals(
            "Closure size: 3508 -> 3506 (356 paths added, 358 paths removed, delta -2, disk usage +6.4GiB).",
            lines.last(),
        )
        // Column alignment survives: no trimming or whitespace collapsing inside the block.
        assertTrue("[D.]  #01  ansi-regex                 6.6.0.tgz -> 6.5.0.tgz" in lines)
        assertEquals(39, lines.count { it.startsWith("[") })
        assertTrue(lines.none { it.startsWith("```") })
    }

    // --- Shapes ---

    @Test
    fun `comment without details is one prose block`() {
        assertEquals(listOf(CommentBlock.Prose("LGTM, **thanks**")), CommentParser.parse("LGTM, **thanks**\n"))
    }

    @Test
    fun `blank and comment-only bodies have no blocks`() {
        assertEquals(emptyList<CommentBlock>(), CommentParser.parse(""))
        assertEquals(emptyList<CommentBlock>(), CommentParser.parse("<!-- marker -->\n"))
    }

    @Test
    fun `prose on both sides of a block`() {
        val blocks = CommentParser.parse(
            "Before\n<details>\n<summary>Plan</summary>\n\nSome *markdown*\n</details>\nAfter",
        )
        assertEquals(
            listOf(
                CommentBlock.Prose("Before"),
                CommentBlock.Details(summary = "Plan", markdown = "Some *markdown*", code = null),
                CommentBlock.Prose("After"),
            ),
            blocks,
        )
    }

    @Test
    fun `body with code and prose is markdown, not code`() {
        val details = CommentParser.parse(
            "<details><summary>Log</summary>\n\n```\nout\n```\n\nand a note\n</details>",
        ).single() as CommentBlock.Details
        assertNull(details.code)
        assertTrue(details.markdown.startsWith("```\nout\n```"))
    }

    @Test
    fun `two code blocks are markdown, not one code block`() {
        val details = CommentParser.parse(
            "<details><summary>Logs</summary>\n\n```\na\n```\n\n```\nb\n```\n</details>",
        ).single() as CommentBlock.Details
        assertNull(details.code)
    }

    @Test
    fun `fence info string and tildes`() {
        val details = CommentParser.parse(
            "<details><summary>Diff</summary>\n\n~~~diff\n- old\n+ new\n~~~\n</details>",
        ).single() as CommentBlock.Details
        assertEquals("- old\n+ new", details.code)
    }

    @Test
    fun `nested details stay inside the outer block`() {
        val blocks = CommentParser.parse(
            "<details><summary>Outer</summary>\n\n<details><summary>Inner</summary>\nx\n</details>\n</details>\ntail",
        )
        assertEquals(2, blocks.size)
        val outer = blocks[0] as CommentBlock.Details
        assertEquals("Outer", outer.summary)
        assertTrue("Inner" in outer.markdown)
        assertEquals(CommentBlock.Prose("tail"), blocks[1])
    }

    @Test
    fun `summary html becomes text with bold markers`() {
        val details = CommentParser.parse(
            "<details><summary><strong>host</strong>: <code>a &amp; b</code>\n  &lt;3</summary>x</details>",
        ).single() as CommentBlock.Details
        assertEquals("**host**: a & b <3", details.summary)
    }

    @Test
    fun `details without summary`() {
        val details = CommentParser.parse("<details>\n\njust text\n</details>").single() as CommentBlock.Details
        assertEquals("", details.summary)
        assertEquals("just text", details.markdown)
    }

    @Test
    fun `unclosed details is left as prose`() {
        val blocks = CommentParser.parse("Intro\n<details><summary>Cut off</summary>\nrest")
        assertTrue(blocks.all { it is CommentBlock.Prose })
        assertTrue(blocks.any { "rest" in (it as CommentBlock.Prose).markdown })
    }
}
