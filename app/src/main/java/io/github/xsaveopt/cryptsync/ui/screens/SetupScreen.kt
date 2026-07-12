package io.github.xsaveopt.cryptsync.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xsaveopt.cryptsync.BuildConfig
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.repo.SetupState
import io.github.xsaveopt.cryptsync.ui.MainViewModel
import io.github.xsaveopt.cryptsync.ui.components.Callout
import io.github.xsaveopt.cryptsync.ui.components.LoadingButton
import io.github.xsaveopt.cryptsync.ui.components.ScreenTitle
import io.github.xsaveopt.cryptsync.ui.components.Section
import io.github.xsaveopt.cryptsync.ui.components.SectionDivider
import io.github.xsaveopt.cryptsync.util.formatSnapshotTime

private enum class SetupMode { FRESH, RESTORE }

@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<SetupMode?>(null) }

    when (mode) {
        null -> ChooseModeStep(
            onFresh = { mode = SetupMode.FRESH },
            onRestore = { mode = SetupMode.RESTORE },
        )

        SetupMode.FRESH -> when (uiState.setupState) {
            SetupState.NeedsCloud -> StepScaffold(onBack = { mode = null }) { ConnectDriveStep(viewModel) }
            SetupState.NeedsPassword, SetupState.NeedsRepository ->
                StepScaffold(onBack = { mode = null }) { CreatePasswordStep(viewModel) }
            SetupState.Ready -> FreshSourcesStep(viewModel)
        }

        SetupMode.RESTORE -> when {
            uiState.setupState == SetupState.NeedsCloud ->
                StepScaffold(onBack = { mode = null }) { ConnectDriveStep(viewModel) }
            uiState.snapshots.isEmpty() ->
                StepScaffold(onBack = { mode = null }) { RestorePasswordStep(viewModel) }
            else ->
                StepScaffold(onBack = { mode = null }) { RestoreBackupStep(viewModel) }
        }
    }
}

@Composable
private fun ChooseModeStep(onFresh: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.setup_welcome_title))
        Section {
            Text(stringResource(R.string.setup_welcome_body), style = MaterialTheme.typography.bodyMedium)
        }
        SectionDivider()
        ChoiceRow(
            title = stringResource(R.string.setup_fresh_title),
            body = stringResource(R.string.setup_fresh_body),
            onClick = onFresh,
        )
        SectionDivider()
        ChoiceRow(
            title = stringResource(R.string.setup_restore_title),
            body = stringResource(R.string.setup_restore_body),
            onClick = onRestore,
        )
    }
}

@Composable
private fun ChoiceRow(title: String, body: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.setup_welcome_title))
        content()
        SectionDivider()
        Section {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.setup_back))
            }
        }
    }
}

@Composable
private fun ConnectDriveStep(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }
    Section(title = stringResource(R.string.home_connect_drive_title)) {
        Text(stringResource(R.string.home_connect_drive_body), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.home_rclone_command),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(stringResource(R.string.home_connect_drive_paste_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.home_paste_token_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton(
            busy = uiState.busy,
            enabled = token.trim().startsWith("{"),
            onClick = { viewModel.connectDrive(token) },
            label = stringResource(R.string.home_connect),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (BuildConfig.DEBUG) {
        SectionDivider()
        DebugBackendSection(viewModel)
    }
}

@Composable
private fun DebugBackendSection(viewModel: MainViewModel) {
    var config by remember { mutableStateOf("") }
    Callout(
        color = MaterialTheme.colorScheme.secondaryContainer,
        title = stringResource(R.string.debug_backend_title),
        body = stringResource(R.string.debug_backend_body),
    )
    Section {
        OutlinedTextField(
            value = config,
            onValueChange = { config = it },
            label = { Text(stringResource(R.string.debug_backend_label)) },
            placeholder = { Text(stringResource(R.string.debug_backend_hint)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = config.contains("type"),
            onClick = { viewModel.connectDebugBackend(config) },
        ) {
            Text(stringResource(R.string.debug_backend_connect))
        }
    }
}

@Composable
private fun CreatePasswordStep(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Section(title = stringResource(R.string.home_password_title)) {
        Text(stringResource(R.string.home_password_body), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.home_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(R.string.home_confirm_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton(
            busy = uiState.busy,
            enabled = password.length >= 8 && password == confirm,
            onClick = { viewModel.createRepository(password) },
            label = stringResource(R.string.home_create_repository),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestorePasswordStep(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    Section(title = stringResource(R.string.setup_restore_password_title)) {
        Text(stringResource(R.string.setup_restore_password_body), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.setup_restore_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        LoadingButton(
            busy = uiState.busy,
            enabled = password.length >= 8,
            onClick = { viewModel.unlockBackupForRestore(password) },
            label = stringResource(R.string.setup_unlock),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestoreBackupStep(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latest = uiState.snapshots.maxByOrNull { it.time } ?: return
    Section(title = stringResource(R.string.setup_pick_snapshot_title)) {
        Text(stringResource(R.string.setup_pick_snapshot_body), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.restore_last_backup, formatSnapshotTime(latest.time)),
            style = MaterialTheme.typography.bodyLarge,
        )
        LoadingButton(
            busy = uiState.busy,
            enabled = true,
            onClick = { viewModel.promptRestoreInPlace(latest.id, duringSetup = true) },
            label = stringResource(R.string.setup_restore_this),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FreshSourcesStep(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            SourcesScreen(viewModel)
        }
        LoadingButton(
            busy = uiState.busy,
            enabled = true,
            onClick = { viewModel.completeOnboarding() },
            label = stringResource(R.string.setup_finish),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
