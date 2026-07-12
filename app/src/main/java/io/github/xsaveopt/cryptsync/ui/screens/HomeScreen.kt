package io.github.xsaveopt.cryptsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.ui.MainViewModel
import io.github.xsaveopt.cryptsync.ui.components.Callout
import io.github.xsaveopt.cryptsync.ui.components.ScreenTitle
import io.github.xsaveopt.cryptsync.ui.components.Section
import io.github.xsaveopt.cryptsync.ui.components.SectionDivider
import io.github.xsaveopt.cryptsync.util.formatBytes
import io.github.xsaveopt.cryptsync.util.formatSnapshotTime

private const val BYTES_PER_GB = 1024L * 1024L * 1024L

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val compressedBytes by viewModel.compressedBytes.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadQuota()
        viewModel.loadSnapshots()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.app_name))

        if (settings.overLimitBytes > 0) {
            Callout(
                color = MaterialTheme.colorScheme.errorContainer,
                title = stringResource(R.string.home_over_limit_title),
                body = stringResource(
                    R.string.home_over_limit_body,
                    formatBytes(settings.overLimitBytes),
                    formatBytes(settings.storageLimitGb.toLong() * BYTES_PER_GB),
                ),
            )
            SectionDivider()
        }

        Callout(
            color = MaterialTheme.colorScheme.errorContainer,
            title = stringResource(R.string.home_live_mirror_title),
            body = stringResource(R.string.home_live_mirror_body),
        )

        SectionDivider()

        Section(title = stringResource(R.string.home_ready)) {
            Text(stringResource(R.string.home_compressed_cache, formatBytes(compressedBytes)))
            uiState.quota?.let { quota ->
                Text(
                    stringResource(
                        R.string.home_drive_usage,
                        formatBytes(quota.usedBytes),
                        formatBytes(quota.totalBytes),
                    ),
                )
            }
            val running = backupProgress.running
            Button(
                onClick = { viewModel.runBackupNow() },
                enabled = !uiState.busy && !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (running) {
                        stringResource(R.string.home_backing_up)
                    } else {
                        stringResource(R.string.home_back_up_now)
                    },
                )
            }
            if (running) {
                val percent = backupProgress.percent
                if (percent in 0..100) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.home_backup_percent, percent))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(
                    onClick = { viewModel.cancelBackup() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
            TextButton(onClick = { viewModel.disconnectDrive() }) {
                Text(stringResource(R.string.home_disconnect_drive))
            }
        }

        SectionDivider()

        val latest = uiState.snapshots.maxByOrNull { it.time }
        val running = backupProgress.running
        Section(title = stringResource(R.string.restore_title)) {
            Text(
                stringResource(R.string.restore_before_body),
                style = MaterialTheme.typography.bodySmall,
            )
            if (latest == null) {
                Text(
                    stringResource(R.string.restore_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    stringResource(R.string.restore_last_backup, formatSnapshotTime(latest.time)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { viewModel.promptRestoreInPlace(latest.id, duringSetup = false) },
                    enabled = !uiState.busy && !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_in_place))
                }
                Text(
                    stringResource(R.string.restore_in_place_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { viewModel.restore(latest.id) },
                    enabled = !uiState.busy && !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.restore_to_app_folder))
                }
                Text(
                    stringResource(R.string.restore_to_app_folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
