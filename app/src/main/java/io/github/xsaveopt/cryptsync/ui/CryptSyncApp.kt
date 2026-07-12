package io.github.xsaveopt.cryptsync.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.ui.screens.ActivityScreen
import io.github.xsaveopt.cryptsync.ui.screens.HomeScreen
import io.github.xsaveopt.cryptsync.ui.screens.LogsScreen
import io.github.xsaveopt.cryptsync.ui.screens.ScheduleScreen
import io.github.xsaveopt.cryptsync.ui.screens.SecurityScreen
import io.github.xsaveopt.cryptsync.ui.screens.SetupScreen
import io.github.xsaveopt.cryptsync.ui.screens.SourcesScreen

private enum class Destination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    SOURCES("sources", R.string.nav_sources, Icons.Filled.Source),
    SCHEDULE("schedule", R.string.nav_schedule, Icons.Filled.Schedule),
    SECURITY("security", R.string.nav_security, Icons.Filled.Lock),
    ACTIVITY("activity", R.string.nav_activity, Icons.Filled.History),
    LOGS("logs", R.string.nav_logs, Icons.AutoMirrored.Filled.ListAlt),
}

@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    directories: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.restore_confirm_body))
                if (directories.isEmpty()) {
                    Text(stringResource(R.string.restore_confirm_no_dirs))
                } else {
                    Text(stringResource(R.string.restore_confirm_dirs_label))
                    directories.forEach { dir ->
                        Text(
                            dir,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm_proceed)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.restore_confirm_cancel)) }
        },
    )
}

@Composable
fun CryptSyncApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    RequestNotificationPermission()

    val restorePrompt by viewModel.restorePrompt.collectAsStateWithLifecycle()
    restorePrompt?.let { prompt ->
        RestoreConfirmDialog(
            directories = prompt.directories,
            onConfirm = { viewModel.confirmRestore() },
            onDismiss = { viewModel.dismissRestorePrompt() },
        )
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (settings.onboardingComplete) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = stringResource(destination.label)) },
                            label = {
                                Text(
                                    stringResource(destination.label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (!settings.onboardingComplete) {
            Box(modifier = Modifier.padding(padding)) { SetupScreen(viewModel) }
        } else {
            NavHost(
                navController = navController,
                startDestination = Destination.HOME.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.HOME.route) { HomeScreen(viewModel) }
                composable(Destination.SOURCES.route) { SourcesScreen(viewModel) }
                composable(Destination.SCHEDULE.route) { ScheduleScreen(viewModel) }
                composable(Destination.SECURITY.route) { SecurityScreen(viewModel) }
                composable(Destination.ACTIVITY.route) { ActivityScreen(viewModel) }
                composable(Destination.LOGS.route) { LogsScreen(viewModel) }
            }
        }
    }
}
