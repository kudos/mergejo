package dev.cremin.mergejo.domain.model

/**
 * The structured content of a Renovate PR body. Renovate renders every PR from
 * the same template — summary table → warnings/notes → release notes → config
 * → rebase checkbox → footer — so each part maps onto a field here and the UI
 * can present it natively instead of dumping the raw markdown.
 */
data class RenovateBody(
    /** One per row of the summary table (grouped/monorepo PRs have several). */
    val updates: List<DependencyUpdate> = emptyList(),
    /** CVE/GHSA ids referenced anywhere in the body, de-duplicated. */
    val advisories: List<Advisory> = emptyList(),
    /** Sections between the table and the release notes (warnings, vulnerability details, prBodyNotes). */
    val notices: List<Notice> = emptyList(),
    /** One per `<details>` block under "Release Notes". */
    val releaseNotes: List<PackageReleaseNotes> = emptyList(),
    val config: RenovateConfig = RenovateConfig(),
) {
    /** False when the body didn't follow Renovate's template (show it raw instead). */
    val isStructured: Boolean get() = updates.isNotEmpty() || releaseNotes.isNotEmpty()

    val releaseCount: Int get() = releaseNotes.sumOf { it.releases.size }
}

/** A single row of Renovate's `| Package | Type | Update | Change | … |` table. */
data class DependencyUpdate(
    val name: String,
    /** Homepage/registry link on the package name. */
    val url: String? = null,
    /** The `([source](…))` link, usually the repository. */
    val sourceUrl: String? = null,
    /** The Type column: `dependencies`, `devDependencies`, `action`, `stage`, … */
    val depType: String? = null,
    val updateType: UpdateType = UpdateType.OTHER,
    /** The Update column verbatim (e.g. `lockFileMaintenance`), for types [UpdateType] doesn't model. */
    val updateLabel: String? = null,
    /** Versions as Renovate wrote them, range prefixes included (`^0.77.0`, `v4`). */
    val from: String? = null,
    val to: String? = null,
    /** The Change column when it isn't a `from → to` pair (e.g. "All locks refreshed"). */
    val changeText: String? = null,
    /** renovatebot.com diff link wrapped around the Change column. */
    val diffUrl: String? = null,
    /** The Pending column: a newer version Renovate is holding back, e.g. `4.12.30 (+1)`. */
    val pending: String? = null,
)

/** A security advisory referenced by the PR. */
data class Advisory(val id: String, val url: String)

data class Notice(val kind: Kind, val markdown: String) {
    enum class Kind { WARNING, SECURITY, NOTE }
}

data class PackageReleaseNotes(
    /** The `<summary>`, e.g. `koajs/compress (koa-compress)`. */
    val title: String,
    val releases: List<Release>,
)

data class Release(
    /** As written in the heading, e.g. `v5.1.1`. */
    val version: String,
    val url: String? = null,
    /** The release name after the heading link, if any. */
    val title: String? = null,
    val compareUrl: String? = null,
    /** Cleaned markdown for this release only. */
    val notesMarkdown: String = "",
)

/** What one of Renovate's schedules governs ("Branch creation", "Automerge") and when it allows it. */
data class ScheduleEntry(val label: String, val times: List<String>)

/**
 * The "Configuration" section. [settings] holds every `emoji **Key**: value`
 * line verbatim (Schedule, Automerge, Rebasing, Ignore, Immortal, …); [schedule] and [timezone]
 * are the Schedule setting parsed, from either of the formats Renovate has used.
 */
data class RenovateConfig(
    val settings: Map<String, String> = emptyMap(),
    val schedule: List<ScheduleEntry> = emptyList(),
    val timezone: String? = null,
) {
    val rebasing: String? get() = settings["Rebasing"]

    /** null when Renovate didn't say. */
    val automerge: Boolean?
        get() = settings["Automerge"]?.let { it.trimStart().startsWith("Enabled", ignoreCase = true) }

    /** Renovate recreates an immortal PR after it's closed, so closing doesn't dismiss it. */
    val immortal: Boolean get() = "Immortal" in settings
}
