package dev.cremin.gitops.ui.screen.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import dev.cremin.gitops.R
import dev.cremin.gitops.domain.model.CheckRun
import dev.cremin.gitops.domain.model.MergeStyle
import dev.cremin.gitops.domain.model.RenovatePr
import dev.cremin.gitops.ui.component.CiStatusBadge
import dev.cremin.gitops.ui.component.SecurityChip
import dev.cremin.gitops.ui.component.UpdateTypeChip

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
                Text(stringResource(R.string.detail_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> DetailContent(pr = pr, modifier = Modifier.padding(padding))
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
            text = { Text(stringResource(R.string.confirm_close_message)) },
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

@Composable
private fun DetailContent(pr: RenovatePr, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(pr.repoSlug, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pr.title, style = MaterialTheme.typography.titleLarge)
        pr.versionChange?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CiStatusBadge(pr.ciStatus)
            UpdateTypeChip(pr.updateType)
            if (pr.isSecurity) SecurityChip()
        }
        Text(
            text = "${stringResource(R.string.detail_branch)}: ${pr.branch} → ${pr.baseBranch}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (pr.mergeable) {
            false -> Text(stringResource(R.string.detail_conflict), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            true -> Text(stringResource(R.string.detail_mergeable), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            null -> Unit
        }

        HorizontalDivider()
        Text(stringResource(R.string.detail_checks), style = MaterialTheme.typography.titleMedium)
        if (pr.checks.isEmpty()) {
            Text(stringResource(R.string.detail_no_checks), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            pr.checks.forEach { CheckRow(it) }
        }

        if (pr.bodyMarkdown.isNotBlank()) {
            HorizontalDivider()
            Text(stringResource(R.string.detail_release_notes), style = MaterialTheme.typography.titleMedium)
            Markdown(content = pr.bodyMarkdown, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CheckRow(check: CheckRun) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CiStatusBadge(check.status)
        Column(Modifier.weight(1f)) {
            Text(check.context.ifBlank { "check" }, style = MaterialTheme.typography.bodyMedium)
            if (check.description.isNotBlank()) {
                Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
