package dev.cremin.gitops.domain

import dev.cremin.gitops.domain.model.UpdateType

/**
 * Extracts dependency metadata from a Renovate PR's title and body. Renovate
 * puts a summary table at the very top of the body (e.g. a "Change" column like
 * `1.2.3` -> `1.3.0`) and encodes the dependency + target version in the title
 * (e.g. "Update dependency react to v18.3.1"). All heuristic, all unit-tested.
 */
object RenovatePrParser {

    data class Parsed(
        val dependency: String?,
        val currentVersion: String?,
        val newVersion: String?,
        val updateType: UpdateType,
        val isSecurity: Boolean,
    )

    // `1.2.3` -> `1.3.0`  /  v1.2.3 -> v1.3.0  (first occurrence is Renovate's table row)
    private val changeRegex =
        Regex("""`?v?([0-9][0-9A-Za-z.\-+]*)`?\s*->\s*`?v?([0-9][0-9A-Za-z.\-+]*)`?""")
    private val titleToRegex = Regex("""(?i)\bto\s+v?([0-9][0-9A-Za-z.\-+]*)""")
    private val depTitleRegexes = listOf(
        Regex("""(?i)^Update dependency\s+(\S+)\s+to\b"""),
        Regex("""(?i)^Update\s+(\S+?)\s+(?:monorepo|Docker tag|action|digest|orb)\b"""),
        Regex("""(?i)^Update\s+(\S+)\s+to\b"""),
    )
    private val parenTypeRegex = Regex("""(?i)\((major|minor|patch|digest|pin)\)""")
    private val securityRegex =
        Regex("""(?i)(GHSA-[0-9a-z]{4}|CVE-\d{4}-\d+|vulnerabilit|security (?:update|fix|advisor))""")
    private val digestRegex = Regex("""(?i)(^[0-9a-f]{7,40}$|^sha256:)""")
    private val semverRegex = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?""")

    fun parse(title: String, body: String): Parsed {
        val change = changeRegex.find(body)
        val currentVersion = change?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val newVersion = change?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: titleToRegex.find(title)?.groupValues?.getOrNull(1)
        return Parsed(
            dependency = parseDependency(title),
            currentVersion = currentVersion,
            newVersion = newVersion,
            updateType = parseUpdateType(title, currentVersion, newVersion),
            isSecurity = securityRegex.containsMatchIn(body),
        )
    }

    private fun parseDependency(title: String): String? {
        for (regex in depTitleRegexes) {
            val match = regex.find(title)
            if (match != null) return match.groupValues[1].trim('`', '"')
        }
        return null
    }

    private fun parseUpdateType(title: String, current: String?, new: String?): UpdateType {
        parenTypeRegex.find(title)?.let {
            return when (it.groupValues[1].lowercase()) {
                "major" -> UpdateType.MAJOR
                "minor" -> UpdateType.MINOR
                "patch" -> UpdateType.PATCH
                "digest" -> UpdateType.DIGEST
                "pin" -> UpdateType.PIN
                else -> UpdateType.OTHER
            }
        }
        if (new != null && digestRegex.containsMatchIn(new)) return UpdateType.DIGEST
        if (current != null && new != null) {
            val c = semverRegex.find(current)
            val n = semverRegex.find(new)
            if (c != null && n != null) {
                val (cMaj, cMin, cPat) = c.destructured
                val (nMaj, nMin, nPat) = n.destructured
                return when {
                    cMaj != nMaj -> UpdateType.MAJOR
                    cMin != nMin -> UpdateType.MINOR
                    cPat.ifEmpty { "0" } != nPat.ifEmpty { "0" } -> UpdateType.PATCH
                    else -> UpdateType.OTHER
                }
            }
        }
        return UpdateType.OTHER
    }
}
