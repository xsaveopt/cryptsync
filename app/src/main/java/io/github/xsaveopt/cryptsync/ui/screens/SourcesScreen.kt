package io.github.xsaveopt.cryptsync.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.ui.MainViewModel
import io.github.xsaveopt.cryptsync.ui.components.Callout
import io.github.xsaveopt.cryptsync.ui.components.ScreenTitle
import io.github.xsaveopt.cryptsync.ui.components.Section
import io.github.xsaveopt.cryptsync.ui.components.SectionDivider
import java.io.File

private data class KnownDir(@StringRes val label: Int, val path: String)

@Composable
fun SourcesScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val root = Environment.getExternalStorageDirectory().absolutePath

    val knownDirs = listOf(
        KnownDir(R.string.sources_dir_camera, "$root/DCIM"),
        KnownDir(R.string.sources_dir_pictures, "$root/Pictures"),
        KnownDir(R.string.sources_dir_movies, "$root/Movies"),
        KnownDir(R.string.sources_dir_downloads, "$root/Download"),
        KnownDir(R.string.sources_dir_whatsapp, "$root/Android/media/com.whatsapp/WhatsApp"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.sources_title))

        Callout(
            color = MaterialTheme.colorScheme.secondaryContainer,
            title = stringResource(R.string.sources_what_can_title),
            body = stringResource(R.string.sources_what_can_body),
        )

        SectionDivider()

        AllFilesAccessSection()

        SectionDivider()

        BackupLocationsSection(viewModel, settings.backupLocations, knownDirs)
    }
}

@Composable
private fun BackupLocationsSection(
    viewModel: MainViewModel,
    locations: Set<String>,
    knownDirs: List<KnownDir>,
) {
    var newPath by remember { mutableStateOf("") }
    Section(title = stringResource(R.string.sources_locations_title)) {
        Text(
            stringResource(R.string.sources_locations_body),
            style = MaterialTheme.typography.bodySmall,
        )
        MediaPermissionRow()

        Text(
            stringResource(R.string.sources_quick_add),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            knownDirs.forEach { dir ->
                OutlinedButton(
                    onClick = { viewModel.setBackupLocations(locations + dir.path) },
                    enabled = dir.path !in locations,
                ) {
                    Text(stringResource(dir.label))
                }
            }
        }

        if (locations.isEmpty()) {
            Text(
                stringResource(R.string.sources_locations_empty),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        locations.forEach { path ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (File(path).exists()) path else stringResource(R.string.sources_path_not_present, path),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { viewModel.setBackupLocations(locations - path) }) {
                    Text(stringResource(R.string.sources_remove))
                }
            }
        }

        OutlinedTextField(
            value = newPath,
            onValueChange = { newPath = it },
            label = { Text(stringResource(R.string.sources_extra_path_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = newPath.isNotBlank() && File(newPath).exists(),
            onClick = {
                viewModel.setBackupLocations(locations + newPath.trim())
                newPath = ""
            },
        ) {
            Text(stringResource(R.string.sources_add_path))
        }
    }
}

@Composable
private fun MediaPermissionRow() {
    val context = LocalContext.current
    fun granted(): Boolean {
        if (Environment.isExternalStorageManager()) return true
        return listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        ).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasAccess by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasAccess = granted() }

    if (hasAccess) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.sources_media_permission_body), style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                ),
            )
        }) {
            Text(stringResource(R.string.sources_media_permission_grant))
        }
    }
}

@Composable
private fun AllFilesAccessSection() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { granted = Environment.isExternalStorageManager() }

    Section(title = stringResource(R.string.sources_full_access_title)) {
        Text(
            stringResource(R.string.sources_full_access_body),
            style = MaterialTheme.typography.bodySmall,
        )
        if (granted) {
            Text(stringResource(R.string.sources_full_access_granted), style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                launcher.launch(intent)
            }) {
                Text(stringResource(R.string.sources_full_access_grant))
            }
        }
    }
}

