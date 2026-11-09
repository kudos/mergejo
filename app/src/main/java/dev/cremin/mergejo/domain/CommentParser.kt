package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.CommentBlock

/**
 * Splits a PR comment into markdown and its top-level `<details>` blocks. Bots put long output in
 * `<details>` (CI closure diffs, plan output, coverage), and the markdown renderer can't collapse
 * them, so the detail screen shows each block as its own expandable card. Nested `<details>` stay
 * inside their block's markdown.
 */
object CommentParser {

    private val detailsTag = Regex("""(?i)<details\b[^>]*>|</details\s*>""")
    private val summaryTag = Regex("""(?is)<summary\b[^>]*>(.*?)</summary\s*>""")
    private val boldTag = Regex("""(?i)</?(?:b|strong)>""")
    private val anyTag = Regex("""<[^>]+>""")
    private val whitespace = Regex("""\s+""")
    private val fenceOpen = Regex("""^(`{3,}|~{3,})[^\n]*\n""")

    fun parse(body: String): List<CommentBlock> {
        val text = body.replace("\r\n", "\n")
        val blocks = mutableListOf<CommentBlock>()
        fun prose(from: Int, to: Int) {
            val markdown = RenovateMarkdown.clean(text.substring(from, to))
            if (markdown.isNotBlank()) blocks += CommentBlock.Prose(markdown)
        }

        var depth = 0
        var proseStart = 0
        var openStart = 0 // where the current top-level <details> tag starts
        var innerStart = 0 // just after it
        for (tag in detailsTag.findAll(text)) {
            if (!tag.value.startsWith("</")) {
                if (depth++ == 0) {
                    openStart = tag.range.first
                    innerStart = tag.range.last + 1
                }
            } else if (depth > 0 && --depth == 0) {
                prose(proseStart, openStart)
                blocks += details(text.substring(innerStart, tag.range.first))
                proseStart = tag.range.last + 1
            }
        }
        // An unclosed <details> is left to the markdown renderer as it is.
        prose(proseStart, text.length)
        return blocks
    }

    private fun details(inner: String): CommentBlock.Details {
        // Only a <summary> that comes first belongs to this block, not to a nested one.
        val summary = summaryTag.find(inner)?.takeIf { inner.substring(0, it.range.first).isBlank() }
        val content = (if (summary != null) inner.substring(summary.range.last + 1) else inner).trim('\n')
        val code = singleCodeBlock(content.trim())
        return CommentBlock.Details(
            summary = summary?.groupValues?.get(1)?.let(::inlineText).orEmpty(),
            markdown = if (code != null) "" else RenovateMarkdown.clean(content),
            code = code,
        )
    }

    /** The text of [content] if it is exactly one fenced code block, else null. */
    private fun singleCodeBlock(content: String): String? {
        val open = fenceOpen.find(content) ?: return null
        val fence = open.groupValues[1]
        val lines = content.substring(open.range.last + 1).lines()
        val close = lines.indexOfFirst { it.trim().startsWith(fence) }
        // The closing fence has to be the last line, so a second block or trailing prose doesn't count.
        if (close != lines.lastIndex || lines[close].trim().trimStart(fence[0]).isNotEmpty()) return null
        return lines.subList(0, close).joinToString("\n")
    }

    private fun inlineText(html: String): String = html
        .replace(boldTag, "**")
        .replace(anyTag, "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(whitespace, " ")
        .trim()
}
