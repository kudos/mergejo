package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.Advisory
import dev.cremin.mergejo.domain.model.DependencyUpdate
import dev.cremin.mergejo.domain.model.Notice
import dev.cremin.mergejo.domain.model.PackageReleaseNotes
import dev.cremin.mergejo.domain.model.Release
import dev.cremin.mergejo.domain.model.RenovateBody
import dev.cremin.mergejo.domain.model.RenovateConfig
import dev.cremin.mergejo.domain.model.UpdateType

/**
 * Parses a Renovate PR body into a [RenovateBody]. Renovate's PR template is
 * `header · table · warnings · notes · changelogs · configDescription · controls · footer`:
 *
 * ```
 * This PR contains the following updates:
 *
 * | Package | Type | Update | Change | Pending |
 * |---|---|---|---|---|
 * | [hono](…) | dependencies | patch | [`4.12.27` → `4.12.28`](…) | `4.12.30` (+1) |
 *
 * ---
 * ### ⚠ Dependency Lookup Warnings ⚠        ← warnings / vulnerability notes (optional)
 * ---
 * ### Release Notes
 * <details>
 * <summary>honojs/hono (hono)</summary>
 * ### [`v4.12.28`](…): title
 * [Compare Source](…)
 * …
 * </details>
 * ---
 * ### Configuration
 * 📅 **Schedule**: …
 * 🚦 **Automerge**: …
 * ---
 *  - [ ] <!-- rebase-check -->If you want to rebase/retry this PR, check this box
 * ```
 *
 * Each region is located by its anchor (table separator, `### Release Notes`,
 * `### Configuration`) rather than by splitting on `---`, because changelog
 * text inside the release notes can contain its own rules and headings.
 */
object RenovateBodyParser {

    private val tableSeparator = Regex("""^\|(\s*:?-{3,}:?\s*\|)+$""")
    private val cellSplit = Regex("""(?<!\\)\|""")
    private val mdLink = Regex("""\[([^\]]*)]\(([^)\s]+)\)""")
    private val arrow = Regex("""\s*(?:→|->|=>)\s*""")
    private val rule = Regex("""^\s*(?:-{3,}|\*{3,})\s*$""")
    private val releaseNotesHeading = Regex("""^#{2,3}\s+Release Notes\s*$""", RegexOption.IGNORE_CASE)
    private val configHeading = Regex("""^#{2,3}\s+Configuration\s*$""", RegexOption.IGNORE_CASE)
    private val detailsOpen = Regex("""(?i)<details\b""")
    private val detailsOpenTag = Regex("""(?i)<details[^>]*>""")
    private val detailsClose = Regex("""(?i)</details>""")
    private val summary = Regex("""(?is)<summary>(.*?)</summary>""")
    // ### [`v5.1.1`](https://…): title   |   ### `v5.1.1`
    private val releaseHeading = Regex("""^###\s+(?:\[`?([^`\]]+?)`?]\(([^)\s]+)\)|`([^`]+)`)(.*)$""")
    private val compareSource = Regex("""^\[Compare Source]\(([^)\s]+)\)\s*$""", RegexOption.IGNORE_CASE)
    private val configLine = Regex("""^\S+\s+\*\*([^*]+)\*\*:\s*(.+)$""")
    private val warningHeading = Regex("""(?m)^(?:#{1,4}\s*)?⚠""")
    private val securityHeading = Regex("""(?im)^#{1,4}\s.*(?:vulnerabilit|\bCVE-|\bGHSA-)""")
    private val linkedAdvisory = Regex("""\[`?(CVE-\d{4}-\d{4,}|GHSA(?:-[0-9a-z]{4}){3})`?]\(([^)\s]+)\)""")
    private val bareAdvisory = Regex("""\b(CVE-\d{4}-\d{4,}|GHSA(?:-[0-9a-z]{4}){3})\b""")
    // Hex with at least one letter, so date-style versions (`20240101`) aren't digests.
    private val digest = Regex("""(?i)^(?:(?=[0-9]*[a-f])[0-9a-f]{7,64}|sha256:[0-9a-f]+)$""")
    private val semver = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

    private const val REBASE_MARKER = "<!-- rebase-check -->"

    fun parse(body: String): RenovateBody {
        val lines = body.replace("\r\n", "\n").lines()

        val notesStart = lines.indexOfFirst { releaseNotesHeading.matches(it.trim()) }.takeIf { it >= 0 }
        val table = findTable(lines, until = notesStart ?: lines.size)

        val (releaseNotes, notesEnd) = if (notesStart != null) {
            parseReleaseNotes(lines, notesStart)
        } else {
            emptyList<PackageReleaseNotes>() to (table?.end ?: 0)
        }

        val configStart = (notesEnd until lines.size)
            .firstOrNull { configHeading.matches(lines[it].trim()) }
        val controlsStart = (notesEnd until lines.size)
            .firstOrNull { REBASE_MARKER in lines[it] }

        val notices = if (table != null) {
            val end = notesStart ?: configStart ?: controlsStart ?: lines.size
            parseNotices(lines.subList(table.end, maxOf(table.end, end)))
        } else {
            emptyList()
        }

        return RenovateBody(
            updates = table?.let(::toUpdates).orEmpty(),
            advisories = parseAdvisories(body),
            notices = notices,
            releaseNotes = releaseNotes,
            config = configStart?.let { parseConfig(lines, it) } ?: RenovateConfig(),
        )
    }

    /**
     * Classifies a version bump when Renovate didn't say: digest target,
     * else the first differing semver component (single segment → major).
     */
    fun inferUpdateType(from: String?, to: String?): UpdateType {
        val target = to?.let(::normalizeVersion) ?: return UpdateType.OTHER
        if (digest.matches(target)) return UpdateType.DIGEST
        val c = from?.let { semver.find(normalizeVersion(it)) } ?: return UpdateType.OTHER
        val n = semver.find(target) ?: return UpdateType.OTHER
        fun String.orZero() = ifEmpty { "0" }
        val (cMaj, cMin, cPat) = c.destructured
        val (nMaj, nMin, nPat) = n.destructured
        return when {
            cMaj != nMaj -> UpdateType.MAJOR
            cMin.orZero() != nMin.orZero() -> UpdateType.MINOR
            cPat.orZero() != nPat.orZero() -> UpdateType.PATCH
            else -> UpdateType.OTHER
        }
    }

    /** Strips range operators and a leading `v`: `^0.77.0` → `0.77.0`, `v4` → `4`. */
    fun normalizeVersion(version: String): String =
        version.trim().trimStart('~', '^', '=', '<', '>', ' ').removePrefix("v")

    fun toUpdateType(label: String): UpdateType = when (label.lowercase()) {
        "major" -> UpdateType.MAJOR
        "minor" -> UpdateType.MINOR
        "patch" -> UpdateType.PATCH
        "digest" -> UpdateType.DIGEST
        "pin", "pindigest" -> UpdateType.PIN
        else -> UpdateType.OTHER
    }

    // --- Summary table ---

    private class Table(val columns: List<String>, val rows: List<List<String>>, val end: Int)

    private fun findTable(lines: List<String>, until: Int): Table? {
        for (i in 0 until minOf(until, lines.size) - 1) {
            val header = lines[i].trim()
            if (!header.startsWith("|") || !tableSeparator.matches(lines[i + 1].trim())) continue
            val columns = splitRow(header).map { it.lowercase() }
            if ("change" !in columns && "package" !in columns) continue
            var j = i + 2
            val rows = buildList {
                while (j < lines.size && lines[j].trim().startsWith("|")) add(splitRow(lines[j++]))
            }
            return Table(columns, rows, end = j)
        }
        return null
    }

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split(cellSplit).map { it.trim() }

    private fun toUpdates(table: Table): List<DependencyUpdate> = table.rows.map { row ->
        fun cell(name: String): String? =
            table.columns.indexOf(name).takeIf { it >= 0 }?.let(row::getOrNull)?.takeIf { it.isNotBlank() }

        val packageCell = cell("package")
        val links = packageCell?.let { mdLink.findAll(it).toList() }.orEmpty()
        val nameLink = links.firstOrNull { it.groupValues[1].lowercase() !in AUX_LINK_TEXT }
        val name = nameLink?.groupValues?.get(1)
            ?: packageCell?.replace(mdLink, "")?.replace(Regex("""\(\s*\)"""), "")
            ?: cell("update")
            ?: ""

        val changeCell = cell("change")
        val changeLink = changeCell?.let { mdLink.matchEntire(it) }
        val change = (changeLink?.groupValues?.get(1) ?: changeCell)?.let(RenovateMarkdown::clean)
        val parts = change?.split(arrow)?.map { it.trim('`', ' ') }
        val (from, to) = if (parts?.size == 2 && parts.all { it.isNotEmpty() }) parts[0] to parts[1] else null to null

        val updateLabel = cell("update")
        DependencyUpdate(
            name = RenovateMarkdown.clean(name).trim('`', ' '),
            url = nameLink?.groupValues?.get(2),
            sourceUrl = links.firstOrNull { it.groupValues[1].equals("source", ignoreCase = true) }?.groupValues?.get(2),
            depType = cell("type"),
            updateType = updateLabel?.let(::toUpdateType)?.takeIf { it != UpdateType.OTHER }
                ?: inferUpdateType(from, to),
            updateLabel = updateLabel,
            from = from,
            to = to,
            changeText = if (from == null) change?.replace("`", "") else null,
            diffUrl = changeLink?.groupValues?.get(2),
            pending = cell("pending")?.replace("`", ""),
        )
    }

    // --- Warnings / notes between the table and the release notes ---

    private fun parseNotices(lines: List<String>): List<Notice> {
        val chunks = mutableListOf<MutableList<String>>(mutableListOf())
        for (line in lines) {
            if (rule.matches(line) || REBASE_MARKER in line) chunks += mutableListOf<String>() else chunks.last() += line
        }
        return chunks
            .map { RenovateMarkdown.clean(it.joinToString("\n")) }
            .filter { it.isNotBlank() }
            .map { md ->
                val kind = when {
                    warningHeading.containsMatchIn(md) -> Notice.Kind.WARNING
                    securityHeading.containsMatchIn(md) -> Notice.Kind.SECURITY
                    else -> Notice.Kind.NOTE
                }
                Notice(kind, md)
            }
    }

    // --- Release notes ---

    /** Returns the per-package notes and the index of the first line after them. */
    private fun parseReleaseNotes(lines: List<String>, heading: Int): Pair<List<PackageReleaseNotes>, Int> {
        val packages = mutableListOf<PackageReleaseNotes>()
        var i = heading + 1
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }
            if (!detailsOpen.containsMatchIn(line)) break
            // Collect up to the matching </details>; changelogs can nest their own.
            val block = mutableListOf<String>()
            var depth = 0
            do {
                depth += detailsOpen.findAll(lines[i]).count() - detailsClose.findAll(lines[i]).count()
                block += lines[i++]
            } while (depth > 0 && i < lines.size)
            packages += parsePackage(block.joinToString("\n"))
        }
        return packages to i
    }

    private fun parsePackage(block: String): PackageReleaseNotes {
        val summaryMatch = summary.find(block)
        val title = summaryMatch?.groupValues?.get(1)?.let(RenovateMarkdown::clean).orEmpty()
        // Strip the outer <details>, its <summary> and the matching </details>; nested ones stay.
        val inner = block
            .let { if (summaryMatch != null) it.removeRange(summaryMatch.range) else it }
            .replaceFirst(detailsOpenTag, "")
            .let { it.substring(0, it.lastIndexOf("</details>", ignoreCase = true).takeIf { i -> i >= 0 } ?: it.length) }

        val releases = mutableListOf<Release>()
        var current: Release? = null
        val notes = StringBuilder()
        fun flush() {
            current?.let { releases += it.copy(notesMarkdown = RenovateMarkdown.clean(notes.toString())) }
            notes.clear()
        }
        for (line in inner.lines()) {
            val heading = releaseHeading.matchEntire(line.trimEnd())
            val compare = compareSource.matchEntire(line.trim())
            when {
                heading != null -> {
                    flush()
                    val (linkedVersion, url, codeVersion, rest) = heading.destructured
                    current = Release(
                        version = linkedVersion.ifEmpty { codeVersion }.trim(),
                        url = url.ifEmpty { null },
                        title = rest.trim().trimStart(':', ' ').trim().ifEmpty { null },
                    )
                }
                compare != null && current != null && current.compareUrl == null ->
                    current = current.copy(compareUrl = compare.groupValues[1])
                current != null -> notes.appendLine(line)
                // Text before the first release heading has nowhere to live; changelog
                // preambles are rare and the PR is one tap away in the browser.
            }
        }
        flush()
        return PackageReleaseNotes(title = title, releases = releases)
    }

    // --- Configuration ---

    private fun parseConfig(lines: List<String>, heading: Int): RenovateConfig {
        val settings = linkedMapOf<String, String>()
        for (i in heading + 1 until lines.size) {
            val line = lines[i].trim()
            if (rule.matches(line) || REBASE_MARKER in line) break
            configLine.matchEntire(line)?.let { settings[it.groupValues[1].trim()] = it.groupValues[2].trim() }
        }
        return RenovateConfig(settings)
    }

    // --- Advisories ---

    private fun parseAdvisories(body: String): List<Advisory> {
        val found = linkedMapOf<String, String>()
        linkedAdvisory.findAll(body).forEach { found.putIfAbsent(it.groupValues[1], it.groupValues[2]) }
        bareAdvisory.findAll(body).forEach { found.putIfAbsent(it.groupValues[1], defaultAdvisoryUrl(it.groupValues[1])) }
        return found.map { (id, url) -> Advisory(id, url) }
    }

    private fun defaultAdvisoryUrl(id: String): String =
        if (id.startsWith("GHSA")) "https://github.com/advisories/$id" else "https://nvd.nist.gov/vuln/detail/$id"

    private val AUX_LINK_TEXT = setOf("source", "changelog", "homepage")
}
