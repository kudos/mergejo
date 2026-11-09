package dev.cremin.mergejo.ui.screen.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import dev.cremin.mergejo.R
import dev.cremin.mergejo.domain.model.Advisory
import dev.cremin.mergejo.domain.model.DependencyUpdate
import dev.cremin.mergejo.domain.model.Notice
import dev.cremin.mergejo.domain.model.PackageReleaseNotes
import dev.cremin.mergejo.domain.model.Release
import dev.cremin.mergejo.domain.model.RenovateConfig
import dev.cremin.mergejo.ui.component.UpdateTypeChip

/** Releases shown per package before "Show all" — big catch-ups can list dozens. */
private const val COLLAPSED_RELEASE_LIMIT = 5

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DetailText.section, modifier = modifier)
}

@Composable
private fun LinkButton(label: String, url: String?) {
    if (url == null) return
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(label) }
}

// --- Updates (the summary table) ---

@Composable
fun UpdatesSection(updates: List<DependencyUpdate>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(pluralStringResource(R.plurals.detail_updates, updates.size, updates.size))
        updates.forEach { UpdateCard(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateCard(update: DependencyUpdate) {
    // The link buttons bring their own padding, so the card only needs its own below the text without them.
    val hasLinks = update.url != null || update.sourceUrl != null || update.diffUrl != null
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = if (hasLinks) 4.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Without a package (lock file maintenance) the change is the headline.
                Text(
                    update.name.ifBlank { update.changeText ?: update.updateLabel.orEmpty() },
                    style = DetailText.item,
                    modifier = Modifier.weight(1f),
                )
                UpdateTypeChip(update.updateType, label = update.updateLabel)
            }
            update.depType?.let {
                Text(it, style = DetailText.secondary, color = DetailText.secondaryColor)
            }
            val change = when {
                update.from != null -> "${update.from} → ${update.to}"
                update.name.isBlank() -> null
                else -> update.to ?: update.changeText
            }
            change?.let {
                Text(it, style = DetailText.code(DetailText.body), modifier = Modifier.padding(top = 4.dp))
            }
            update.pending?.let {
                Text(stringResource(R.string.detail_pending, it), style = DetailText.secondary, color = DetailText.secondaryColor)
            }
            if (hasLinks) {
                FlowRow {
                    LinkButton(stringResource(R.string.link_package), update.url)
                    LinkButton(stringResource(R.string.link_source), update.sourceUrl)
                    LinkButton(stringResource(R.string.link_diff), update.diffUrl)
                }
            }
        }
    }
}

// --- Security advisories and notices ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvisoriesSection(advisories: List<Advisory>) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(stringResource(R.string.detail_advisories))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            advisories.forEach { advisory ->
                AssistChip(
                    onClick = { runCatching { uriHandler.openUri(advisory.url) } },
                    label = { Text(advisory.id, fontFamily = FontFamily.Monospace) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                        leadingIconContentColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}

@Composable
fun NoticeCard(notice: Notice) {
    val colors = MaterialTheme.colorScheme
    val (container, icon) = when (notice.kind) {
        Notice.Kind.SECURITY -> colors.errorContainer to Icons.Outlined.Lock
        Notice.Kind.WARNING -> colors.tertiaryContainer to Icons.Outlined.WarningAmber
        Notice.Kind.NOTE -> colors.surfaceVariant to Icons.Outlined.Info
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Markdown(
                content = notice.markdown,
                colors = markdownColor(text = LocalContentColor.current),
                typography = DetailText.markdown(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// --- Release notes ---

@Composable
fun ReleaseNotesSection(packages: List<PackageReleaseNotes>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.detail_release_notes))
        packages.forEach { pkg ->
            // A lone package's title just repeats the PR; only label groups.
            if (packages.size > 1 && pkg.title.isNotBlank()) {
                Text(
                    pkg.title,
                    style = DetailText.secondary,
                    color = DetailText.secondaryColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            var showAll by rememberSaveable(pkg.title) { mutableStateOf(false) }
            val visible = if (showAll) pkg.releases else pkg.releases.take(COLLAPSED_RELEASE_LIMIT)
            visible.forEachIndexed { index, release ->
                ReleaseCard(release, initiallyExpanded = index == 0, key = "${pkg.title}/${release.version}")
            }
            val hidden = pkg.releases.size - visible.size
            if (hidden > 0) {
                TextButton(onClick = { showAll = true }) {
                    Text(pluralStringResource(R.plurals.detail_show_more_releases, hidden, hidden))
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Release, initiallyExpanded: Boolean, key: String) {
    var expanded by rememberSaveable(key) { mutableStateOf(initiallyExpanded) }
    val hasNotes = release.notesMarkdown.isNotBlank()
    OutlinedCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasNotes) { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(release.version, style = DetailText.code(DetailText.item))
            Text(
                release.title.orEmpty(),
                style = DetailText.secondary,
                color = DetailText.secondaryColor,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (hasNotes) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                )
            }
        }
        if (expanded && hasNotes) {
            HorizontalDivider()
            // Each release renders on its own, and only when expanded, so a
            // 100-release catch-up doesn't lay out one giant markdown document.
            Markdown(
                content = release.notesMarkdown,
                typography = DetailText.markdown(),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        if (expanded || !hasNotes) {
            Row(Modifier.padding(horizontal = 4.dp)) {
                LinkButton(stringResource(R.string.link_release), release.url)
                LinkButton(stringResource(R.string.link_compare), release.compareUrl)
            }
        }
    }
}

// --- Renovate configuration ---

@Composable
fun RenovateConfigSection(config: RenovateConfig) {
    // Renovate's own settings rarely change a merge decision, so they start collapsed.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(R.string.detail_renovate), Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            )
        }
        if (!expanded) return@Column
        config.automerge?.let {
            ConfigRow(
                stringResource(R.string.config_automerge),
                stringResource(if (it) R.string.config_enabled else R.string.config_disabled),
            )
        }
        if (config.schedule.isNotEmpty()) {
            ConfigRow(
                config.timezone?.let { stringResource(R.string.config_schedule_in, it) } ?: stringResource(R.string.config_schedule),
                config.schedule.joinToString("\n") { "${it.label}: ${it.times.joinToString(", ")}" },
            )
        }
        config.rebasing?.let { ConfigRow(stringResource(R.string.config_rebasing), it) }
        if (config.immortal) {
            Text(stringResource(R.string.config_immortal), style = DetailText.secondary, color = DetailText.secondaryColor)
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Column {
        Text(label, style = DetailText.secondary, color = DetailText.secondaryColor)
        Text(value.removeSuffix("."), style = DetailText.body)
    }
}
