package dev.cremin.mergejo.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cremin.mergejo.R
import dev.cremin.mergejo.domain.model.MergeStyle
import dev.cremin.mergejo.domain.model.PollInterval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledValue(stringResource(R.string.settings_account), ui.userLogin ?: "—")
            LabeledValue(stringResource(R.string.settings_server), viewModel.serverUrl)

            HorizontalDivider()

            Text(stringResource(R.string.settings_default_merge_style), style = MaterialTheme.typography.titleSmall)
            MergeStyle.entries.forEach { style ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = style == ui.defaultMergeStyle,
                            onClick = { viewModel.setMergeStyle(style) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = style == ui.defaultMergeStyle,
                        onClick = { viewModel.setMergeStyle(style) },
                    )
                    Text(
                        text = when (style) {
                            MergeStyle.MERGE -> stringResource(R.string.merge_style_merge)
                            MergeStyle.SQUASH -> stringResource(R.string.merge_style_squash)
                            MergeStyle.REBASE -> stringResource(R.string.merge_style_rebase)
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            HorizontalDivider()

            NotificationSettings(selected = ui.pollInterval, onSelect = viewModel::setPollInterval)

            HorizontalDivider()

            LabeledValue(stringResource(R.string.settings_version), viewModel.versionName)
            Text(
                text = stringResource(R.string.settings_logo_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_sign_out))
            }
        }
    }
}

/**
 * Poll interval picker. Choosing an interval asks for the notification permission first
 * (Android 13+) and only saves it if granted; a warning shows while notifications are
 * blocked in system settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettings(selected: PollInterval, onSelect: (PollInterval) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pendingInterval by remember { mutableStateOf<PollInterval?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var notificationsBlocked by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        notificationsBlocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingInterval?.let(onSelect)
        permissionDenied = !granted
        notificationsBlocked = !granted
        pendingInterval = null
    }

    fun choose(interval: PollInterval) {
        expanded = false
        val needsPermission = interval != PollInterval.OFF &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingInterval = interval
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSelect(interval)
        }
    }

    Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleSmall)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PollInterval.entries.forEach { interval ->
                DropdownMenuItem(
                    text = { Text(stringResource(interval.labelRes())) },
                    onClick = { choose(interval) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.settings_notifications_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (notificationsBlocked && (selected != PollInterval.OFF || permissionDenied)) {
        Text(
            text = stringResource(R.string.settings_notifications_blocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        ) {
            Text(stringResource(R.string.settings_notifications_open_system))
        }
    }
}

private fun PollInterval.labelRes(): Int = when (this) {
    PollInterval.OFF -> R.string.poll_interval_off
    PollInterval.MINUTES_15 -> R.string.poll_interval_15m
    PollInterval.MINUTES_30 -> R.string.poll_interval_30m
    PollInterval.HOURS_1 -> R.string.poll_interval_1h
    PollInterval.HOURS_3 -> R.string.poll_interval_3h
    PollInterval.HOURS_6 -> R.string.poll_interval_6h
    PollInterval.HOURS_12 -> R.string.poll_interval_12h
    PollInterval.HOURS_24 -> R.string.poll_interval_24h
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
