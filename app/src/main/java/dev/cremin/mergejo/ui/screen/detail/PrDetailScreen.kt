package dev.cremin.mergejo.ui.screen.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import dev.cremin.mergejo.R
import dev.cremin.mergejo.domain.RenovateMarkdown
import dev.cremin.mergejo.domain.model.CheckRun
import dev.cremin.mergejo.domain.model.MergeStyle
import dev.cremin.mergejo.domain.model.RenovatePr
import dev.cremin.mergejo.ui.component.CiStatusBadge
import dev.cremin.mergejo.ui.component.MergeableChip
import dev.cremin.mergejo.ui.component.RebasePendingChip
import dev.cremin.mergejo.ui.component.SecurityChip
import dev.cremin.mergejo.ui.component.UpdateTypeChip

@StringRes
private fun MergeStyle.labelRes(): Int = when (this) {
    MergeStyle.MERGE -> R.string.merge_style_merge
    MergeStyle.SQUASH -> R.string.merge_style_squash
    MergeStyle.REBASE -> R.string.merge_style_rebase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrDetailScreen(
    onBack: () -> Unit,
    viewModel: PrDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    val approvedMsg = stringResource(R.string.action_approved)
    val rebaseMsg = stringResource(R.string.rebase_requested)
    val failedFmt = stringResource(R.string.action_failed, "")

    LaunchedEventEffect(viewModel) { event ->
        when (event) {
            DetailEvent.Dismiss -> onBack()
            DetailEvent.Approved -> snackbarHostState.showSnackbar(approvedMsg)
            DetailEvent.RebaseRequested -> snackbarHostState.showSnackbar(rebaseMsg)
            is DetailEvent.Error -> snackbarHostState.showSnackbar(failedFmt + event.text)
        }
    }

    var showMergeDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    val pr = ui.pr

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pr?.dependency ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (pr != null) {
                        IconButton(onClick = { runCatching { uriHandler.openUri(pr.htmlUrl) } }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = stringResource(R.string.detail_open_in_browser))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (pr != null) {
                ActionBar(
                    enabled = !ui.actionInProgress,
                    onMerge = { showMergeDialog = true },
                    onApprove = viewModel::approve,
                    onRebase = viewModel::rebase,
                    onClose = { showCloseDialog = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            ui.loading -> CenterBox(Modifier.padding(padding)) { CircularProgressIndicator() }
            pr == null -> CenterBox(Modifier.padding(padding)) {
                Text(stringResource(R.string.detail_not_found), style = DetailText.body, color = DetailText.secondaryColor)
            }
            else -> DetailContent(pr = pr, comments = ui.comments, modifier = Modifier.padding(padding))
        }
    }

    if (showMergeDialog && pr != null) {
        MergeConfirmDialog(
            defaultStyle = ui.defaultMergeStyle,
            onConfirm = { style ->
                showMergeDialog = false
                viewModel.merge(style)
            },
            onDismiss = { showMergeDialog = false },
        )
    }
    if (showCloseDialog && pr != null) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text(stringResource(R.string.confirm_close_title)) },
            text = {
                Text(
                    stringResource(
                        if (pr.body.config.immortal) R.string.confirm_close_message_immortal else R.string.confirm_close_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloseDialog = false
                    viewModel.close()
                }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(pr: RenovatePr, comments: CommentsState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val body = pr.body
        Text(pr.repoSlug, style = DetailText.secondary, color = DetailText.secondaryColor)
        Text(pr.title, style = DetailText.title)
        // A single update is fully described here; groups get the per-package cards below.
        if (body.updates.size <= 1) {
            pr.versionChange?.let { Text(it, style = DetailText.code(DetailText.item)) }
                ?: pr.changeSummary?.let { Text(it, style = DetailText.body) }
        }
        // Status at a glance. Wraps, since a security update with conflicts and a long type label
        // (lockFileMaintenance) can outgrow a folded screen.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val centred = Modifier.align(Alignment.CenterVertically)
            CiStatusBadge(pr.ciStatus, centred)
            UpdateTypeChip(pr.updateType, centred, label = pr.updateLabel)
            if (pr.isSecurity) SecurityChip(centred)
            pr.mergeable?.let { MergeableChip(it, centred) }
            if (pr.rebaseRequested) RebasePendingChip(centred)
        }
        Text(
            text = "${stringResource(R.string.detail_branch)}: ${pr.branch} → ${pr.baseBranch}",
            style = DetailText.code(DetailText.secondary),
            color = DetailText.secondaryColor,
        )

        if (body.advisories.isNotEmpty()) AdvisoriesSection(body.advisories)
        body.notices.forEach { NoticeCard(it) }
        // A package-less row (lock file maintenance) is already covered by the header.
        if (body.updates.any { it.name.isNotBlank() }) {
            HorizontalDivider()
            UpdatesSection(body.updates)
        }

        HorizontalDivider()
        SectionHeader(stringResource(R.string.detail_checks))
        if (pr.checks.isEmpty()) {
            Text(stringResource(R.string.detail_no_checks), style = DetailText.secondary, color = DetailText.secondaryColor)
        } else {
            pr.checks.forEach { CheckRow(it) }
        }
        // Next to the checks: these are mostly CI output, such as closure diffs.
        CommentsSection(comments)

        if (body.releaseNotes.isNotEmpty()) {
            HorizontalDivider()
            ReleaseNotesSection(body.releaseNotes)
        }
        if (!body.isStructured && pr.bodyMarkdown.isNotBlank()) {
            // Not Renovate's template (or a format we don't know yet): show it as-is.
            HorizontalDivider()
            SectionHeader(stringResource(R.string.detail_description))
            Markdown(
                content = RenovateMarkdown.clean(pr.bodyMarkdown),
                typography = DetailText.markdown(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (body.config.settings.isNotEmpty()) {
            HorizontalDivider()
            RenovateConfigSection(body.config)
        }
    }
}

@Composable
private fun CheckRow(check: CheckRun) {
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CiStatusBadge(check.status)
        Column(Modifier.weight(1f)) {
            Text(check.context.ifBlank { "check" }, style = DetailText.body)
            if (check.description.isNotBlank()) {
                Text(check.description, style = DetailText.secondary, color = DetailText.secondaryColor)
            }
        }
        check.targetUrl?.let { url ->
            IconButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = stringResource(R.string.detail_open_check))
            }
        }
    }
}

@Composable
private fun ActionBar(
    enabled: Boolean,
    onMerge: () -> Unit,
    onApprove: () -> Unit,
    onRebase: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onMerge, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_merge))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onApprove, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_approve))
                }
                OutlinedButton(onClick = onRebase, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_rebase))
                }
                OutlinedButton(onClick = onClose, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun MergeConfirmDialog(
    defaultStyle: MergeStyle,
    onConfirm: (MergeStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(defaultStyle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_merge_title)) },
        text = {
            Column {
                MergeStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = style == selected, onClick = { selected = style })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == selected, onClick = { selected = style })
                        Text(stringResource(style.labelRes()), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_merge)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CenterBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Collects one-shot [DetailEvent]s while the screen is active. */
@Composable
private fun LaunchedEventEffect(
    viewModel: PrDetailViewModel,
    handle: suspend (DetailEvent) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.events.collect { handle(it) }
    }
}
