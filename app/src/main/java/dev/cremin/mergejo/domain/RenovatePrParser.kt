package dev.cremin.mergejo.domain

import dev.cremin.mergejo.domain.model.UpdateType

/**
 * Extracts dependency metadata from a Renovate PR's title and body. Renovate
 * puts a summary table at the very top of the body:
 *
 * ```
 * | Package | Type | Update | Change |
 * |---|---|---|---|
 * | [hono](…) | dependencies | patch | [`4.12.27` → `4.12.28`](…) |
 * ```
 *
 * The **Update** column (`major`/`minor`/`patch`/`digest`/`pin`/…) is the most
 * reliable type signal, so we prefer it; the **Change** column gives the
 * `current → new` versions. Note Renovate uses a Unicode right-arrow `→`
 * (U+2192), not ASCII `->`. Only the table region (everything above the first
 * `---` rule) is scanned, so `x -> y` text in the release notes below can't
 * cause false matches. All heuristic, all unit-tested.
 */
object RenovatePrParser {

    data class Parsed(
        val dependency: String?,
        val currentVersion: String?,
        val newVersion: String?,
        val updateType: UpdateType,
        val isSecurity: Boolean,
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
    // Explicit type: `(minor)` in a title, or the Update column of the table
    // (a lone type keyword sitting between markdown pipes).
    private val parenTypeRegex = Regex("""(?i)\((major|minor|patch|digest|pin)\)""")
    private val tableTypeRegex = Regex(
        """(?i)\|\s*(major|minor|patch|pinDigest|digest|pin|rollback|lockFileMaintenance|bump|replacement|widen)\s*\|"""
    )
    private val securityRegex =
        Regex("""(?i)(GHSA-[0-9a-z]{4}|CVE-\d{4}-\d+|vulnerabilit|security (?:update|fix|advisor))""")
    private val digestRegex = Regex("""(?i)(^[0-9a-f]{7,40}$|^sha256:)""")
    private val semverRegex = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

    fun parse(title: String, body: String): Parsed {
        val table = tableRegion(body)
        val change = changeRegex.find(table)
        val currentVersion = change?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val newVersion = change?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: titleToRegex.find(title)?.groupValues?.getOrNull(1)
        return Parsed(
            dependency = parseDependency(title),
            currentVersion = currentVersion,
            newVersion = newVersion,
            updateType = parseUpdateType(title, table, currentVersion, newVersion),
            isSecurity = securityRegex.containsMatchIn(body),
        )
    }

    /**
     * Everything above Renovate's first horizontal rule — i.e. the summary
     * table, before the release notes. Falls back to the whole body if there's
     * no rule (keeps short/tableless PRs working).
     */
    private fun tableRegion(body: String): String {
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

    private fun parseUpdateType(title: String, table: String, current: String?, new: String?): UpdateType {
        // 1. Explicit `(type)` in the title.
        parenTypeRegex.find(title)?.let { return it.groupValues[1].toUpdateType() }
        // 2. Explicit Update column in the table.
        tableTypeRegex.find(table)?.let { return it.groupValues[1].toUpdateType() }
        // 3. A digest target.
        if (new != null && digestRegex.containsMatchIn(new)) return UpdateType.DIGEST
        // 4. Compare semver components (single-segment versions treated as major).
        if (current != null && new != null) {
            val c = semverRegex.find(current)
            val n = semverRegex.find(new)
            if (c != null && n != null) {
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
        }
        return UpdateType.OTHER
    }

    private fun String.toUpdateType(): UpdateType = when (lowercase()) {
        "major" -> UpdateType.MAJOR
        "minor" -> UpdateType.MINOR
        "patch" -> UpdateType.PATCH
        "digest" -> UpdateType.DIGEST
        "pin", "pindigest" -> UpdateType.PIN
        else -> UpdateType.OTHER
    }
}
