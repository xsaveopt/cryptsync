package io.github.xsaveopt.cryptsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.ui.MainViewModel
import io.github.xsaveopt.cryptsync.ui.components.LoadingButton
import io.github.xsaveopt.cryptsync.ui.components.ScreenTitle
import io.github.xsaveopt.cryptsync.ui.components.Section
import io.github.xsaveopt.cryptsync.ui.components.SectionDivider

@Composable
fun SecurityScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadKeys() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.security_title))

        Section(title = stringResource(R.string.security_change_password)) {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text(stringResource(R.string.security_current_password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.security_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(stringResource(R.string.security_confirm_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            LoadingButton(
                busy = uiState.busy,
                enabled = newPassword.length >= 8 && newPassword == confirm,
                onClick = {
                    viewModel.changePassword(current, newPassword)
                    current = ""; newPassword = ""; confirm = ""
                },
                label = stringResource(R.string.security_change_password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionDivider()

        Section(title = stringResource(R.string.security_saved_keys)) {
            Text(
                stringResource(R.string.security_saved_keys_body),
                style = MaterialTheme.typography.bodySmall,
            )
            if (uiState.keys.isEmpty() && uiState.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        stringResource(R.string.security_keys_loading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            uiState.keys.forEach { key ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            if (key.current) {
                                stringResource(R.string.security_current_key)
                            } else {
                                stringResource(R.string.security_key_named, key.id.take(8))
                            },
                        )
                        Text(key.created, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!key.current) {
                        OutlinedButton(onClick = { viewModel.removeKey(key.id) }) {
                            Text(stringResource(R.string.security_remove))
                        }
                    }
                }
            }
        }

        SectionDivider()

        Section(title = stringResource(R.string.security_integrity_title)) {
            Text(
                stringResource(R.string.security_integrity_body),
                style = MaterialTheme.typography.bodySmall,
            )
            LoadingButton(
                busy = uiState.busy,
                enabled = true,
                onClick = { viewModel.checkIntegrity() },
                label = stringResource(R.string.security_integrity_check),
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.integrity?.let { result ->
                if (result.healthy) {
                    Text(
                        stringResource(R.string.security_integrity_healthy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.security_integrity_damaged),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (result.missingPackIds.isNotEmpty()) {
                        Text(
                            pluralStringResource(
                                R.plurals.security_integrity_missing_packs,
                                result.missingPackIds.size,
                                result.missingPackIds.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        result.missingPackIds.take(5).forEach { id ->
                            Text(
                                stringResource(R.string.security_integrity_missing_pack_id, id.take(12)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.security_integrity_generic_error),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (result.affectedFiles.isNotEmpty()) {
                        Text(
                            stringResource(R.string.security_integrity_affected_title),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        result.affectedFiles.forEach { path ->
                            Text(path, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        stringResource(R.string.security_integrity_repair_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LoadingButton(
                        busy = uiState.busy,
                        enabled = true,
                        onClick = { viewModel.repairAndBackup() },
                        label = stringResource(R.string.security_integrity_repair),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(
                            stringResource(
                                if (showDetails) {
                                    R.string.security_integrity_details_hide
                                } else {
                                    R.string.security_integrity_details_show
                                },
                            ),
                        )
                    }
                    if (showDetails) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                result.details.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
