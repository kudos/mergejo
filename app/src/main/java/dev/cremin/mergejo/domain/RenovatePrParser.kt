package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.RenovateBody
import dev.cremin.mergejo.domain.model.UpdateType

/**
 * Derives the PR-level summary (dependency, versions, update type, security)
 * from a Renovate PR's title and its structured body ([RenovateBodyParser]).
 *
 * The summary table is the primary source: its **Update** column is Renovate's
 * own classification, and for grouped PRs the most significant row wins (a
 * group containing one major is a major). The title and a loose `x → y` scan
 * of the pre-release-notes region are fallbacks for bodies without a table.
 * Note Renovate uses a Unicode right-arrow `→` (U+2192), not ASCII `->`.
 */
object RenovatePrParser {

    data class Parsed(
        val dependency: String?,
        val currentVersion: String?,
        val newVersion: String?,
        val updateType: UpdateType,
        val isSecurity: Boolean,
        val body: RenovateBody,
    )

    // `4.12.27` → `4.12.28`  /  `^0.77.0` → `^0.78.0`  /  `v4` → `v5`
    // Arrow is Renovate's Unicode `→`; ASCII `->`/`=>` accepted as a fallback.
    private val changeRegex = Regex(
        """`?[~^]?v?([0-9][0-9A-Za-z.\-+]*)`?\s*(?:->|=>|→)\s*`?[~^]?v?([0-9][0-9A-Za-z.\-+]*)`?"""
    )
    private val titleToRegex = Regex("""(?i)\bto\s+[~^]?v?([0-9][0-9A-Za-z.\-+]*)""")
    private val depTitleRegexes = listOf(
        Regex("""(?i)^Update dependency\s+(\S+)\s+to\b"""),
        Regex("""(?i)^Update\s+(\S+?)\s+(?:monorepo|Docker tag|action|digest|orb)\b"""),
        Regex("""(?i)^Update\s+(\S+)\s+to\b"""),
    )
    private val parenTypeRegex = Regex("""(?i)\((major|minor|patch|digest|pin)\)""")
    private val securityRegex =
        Regex("""(?i)(GHSA-[0-9a-z]{4}|CVE-\d{4}-\d+|vulnerabilit|security (?:update|fix|advisor))""")
    private val securityTitleRegex = Regex("""(?i)\[security]""")

    fun parse(title: String, body: String): Parsed {
        val structured = RenovateBodyParser.parse(body)
        val primary = structured.updates.firstOrNull { it.to != null }

        // No table → scan above the first rule (so arrows in release notes can't match).
        val loose = if (primary == null) changeRegex.find(beforeFirstRule(body)) else null
        val currentVersion = primary?.from?.let(RenovateBodyParser::normalizeVersion)
            ?: loose?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val newVersion = primary?.to?.let(RenovateBodyParser::normalizeVersion)
            ?: loose?.groupValues?.get(2)?.takeIf { it.isNotBlank() }
            ?: titleToRegex.find(title)?.groupValues?.get(1)

        return Parsed(
            dependency = parseDependency(title) ?: structured.updates.firstOrNull()?.name?.ifBlank { null },
            currentVersion = currentVersion,
            newVersion = newVersion,
            updateType = parseUpdateType(title, structured, currentVersion, newVersion),
            isSecurity = securityTitleRegex.containsMatchIn(title) ||
                structured.advisories.isNotEmpty() ||
                securityRegex.containsMatchIn(body),
            body = structured,
        )
    }

    private fun beforeFirstRule(body: String): String {
        val rule = Regex("""(?m)^\s*---\s*$""").find(body)
        return if (rule != null) body.substring(0, rule.range.first) else body
    }

    private fun parseDependency(title: String): String? {
        for (regex in depTitleRegexes) {
            val match = regex.find(title)
            if (match != null) return match.groupValues[1].trim('`', '"')
        }
        return null
    }

    private fun parseUpdateType(title: String, body: RenovateBody, current: String?, new: String?): UpdateType {
        // 1. Explicit `(type)` in the title.
        parenTypeRegex.find(title)?.let { return RenovateBodyParser.toUpdateType(it.groupValues[1]) }
        // 2. The table rows (Update column, else inferred per row); most significant wins.
        body.updates.map { it.updateType }.filter { it != UpdateType.OTHER }.minByOrNull { it.ordinal }
            ?.let { return it }
        // 3. Digest target / semver comparison of whatever versions we found.
        return RenovateBodyParser.inferUpdateType(current, new)
    }
}
