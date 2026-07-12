package io.github.xsaveopt.cryptsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.data.db.ActivityOutcome
import io.github.xsaveopt.cryptsync.ui.MainViewModel
import io.github.xsaveopt.cryptsync.ui.components.ScreenTitle
import io.github.xsaveopt.cryptsync.ui.components.Section
import io.github.xsaveopt.cryptsync.ui.components.SectionDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScreenTitle(stringResource(R.string.activity_title))
            OutlinedButton(
                onClick = { viewModel.clearActivity() },
                enabled = activity.isNotEmpty(),
                modifier = Modifier.padding(end = 16.dp),
            ) {
                Text(stringResource(R.string.activity_clear))
            }
        }

        Section {
            Text(
                stringResource(R.string.activity_subtitle),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (activity.isEmpty()) {
            SectionDivider()
            Section {
                Text(
                    stringResource(R.string.activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            activity.forEach { entry ->
                SectionDivider()
                Section {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = entry.outcome.color(),
                        )
                        Text(
                            formatter.format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entry.detail.isNotBlank()) {
                        Text(entry.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityOutcome.color(): Color = when (this) {
    ActivityOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
    ActivityOutcome.FAILURE -> MaterialTheme.colorScheme.error
    ActivityOutcome.INFO -> MaterialTheme.colorScheme.onSurface
}
