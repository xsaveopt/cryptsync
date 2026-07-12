package io.github.xsaveopt.cryptsync.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.data.settings.ImageFormat
import io.github.xsaveopt.cryptsync.data.settings.NetworkPolicy
import io.github.xsaveopt.cryptsync.data.settings.ScheduleFrequency
import io.github.xsaveopt.cryptsync.data.settings.VideoCodec
import io.github.xsaveopt.cryptsync.media.CodecSupport
import io.github.xsaveopt.cryptsync.ui.MainViewModel
import io.github.xsaveopt.cryptsync.ui.RepoSize
import io.github.xsaveopt.cryptsync.ui.components.ScreenTitle
import io.github.xsaveopt.cryptsync.ui.components.Section
import io.github.xsaveopt.cryptsync.ui.components.SectionDivider
import io.github.xsaveopt.cryptsync.util.formatBytes

@Composable
fun ScheduleScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val repoSize by viewModel.repoSize.collectAsStateWithLifecycle()
    val hardwareCodecs = remember { CodecSupport.hardwareVideoCodecs() }
    val schedule = settings.schedule
    val compression = settings.compression

    LaunchedEffect(Unit) { viewModel.loadRepoSize() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.schedule_title))

        Section(title = stringResource(R.string.schedule_how_often)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScheduleFrequency.entries.forEach { frequency ->
                    FilterChip(
                        selected = schedule.frequency == frequency,
                        onClick = { viewModel.updateSchedule(schedule.copy(frequency = frequency)) },
                        label = { Text(stringResource(frequency.labelRes)) },
                    )
                }
            }

            if (schedule.frequency == ScheduleFrequency.DAILY ||
                schedule.frequency == ScheduleFrequency.WEEKLY ||
                schedule.frequency == ScheduleFrequency.MONTHLY
            ) {
                Text(stringResource(R.string.schedule_run_at, schedule.hourOfDay))
                Slider(
                    value = schedule.hourOfDay.toFloat(),
                    onValueChange = { viewModel.updateSchedule(schedule.copy(hourOfDay = it.toInt())) },
                    valueRange = 0f..23f,
                    steps = 22,
                )
            }

            SwitchRow(
                label = stringResource(R.string.schedule_charging_only),
                checked = schedule.chargingOnly,
                onChange = { viewModel.updateSchedule(schedule.copy(chargingOnly = it)) },
            )
            SwitchRow(
                label = stringResource(R.string.schedule_wifi_only),
                checked = schedule.networkPolicy == NetworkPolicy.UNMETERED,
                onChange = {
                    val policy = if (it) NetworkPolicy.UNMETERED else NetworkPolicy.ANY
                    viewModel.updateSchedule(schedule.copy(networkPolicy = policy))
                },
            )
        }

        SectionDivider()

        Section(title = stringResource(R.string.schedule_compression)) {
            SwitchRow(
                label = stringResource(R.string.schedule_reencode_media),
                checked = compression.reencodeMedia,
                onChange = { viewModel.updateCompression(compression.copy(reencodeMedia = it)) },
            )
            Text(
                stringResource(R.string.schedule_reencode_media_body),
                style = MaterialTheme.typography.bodySmall,
            )
            if (compression.reencodeMedia) {
                Text(stringResource(R.string.schedule_video_codec))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCodec.entries.forEach { codec ->
                        FilterChip(
                            selected = compression.videoCodec == codec,
                            onClick = { viewModel.updateCompression(compression.copy(videoCodec = codec)) },
                            label = { Text(stringResource(codec.labelRes)) },
                        )
                    }
                }
                VideoCodec.entries.forEach { codec ->
                    val supported = codec in hardwareCodecs
                    Text(
                        stringResource(
                            if (supported) R.string.codec_hw_supported else R.string.codec_hw_unsupported,
                            stringResource(codec.labelRes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (supported) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }

                SteppedValueRow(
                    label = stringResource(R.string.schedule_video_bitrate, compression.videoBitrateKbps),
                    value = compression.videoBitrateKbps,
                    range = 1000..20000,
                    step = 100,
                    onValueChange = { viewModel.updateCompression(compression.copy(videoBitrateKbps = it)) },
                )

                SteppedValueRow(
                    label = stringResource(R.string.schedule_audio_bitrate, compression.audioBitrateKbps),
                    value = compression.audioBitrateKbps,
                    range = 32..256,
                    step = 8,
                    onValueChange = { viewModel.updateCompression(compression.copy(audioBitrateKbps = it)) },
                )

                Text(stringResource(R.string.schedule_image_format))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageFormat.entries.forEach { format ->
                        FilterChip(
                            selected = compression.imageFormat == format,
                            onClick = { viewModel.updateCompression(compression.copy(imageFormat = format)) },
                            label = { Text(stringResource(format.labelRes)) },
                        )
                    }
                }

                SteppedValueRow(
                    label = stringResource(R.string.schedule_image_quality, compression.imageQuality),
                    value = compression.imageQuality,
                    range = 1..100,
                    step = 1,
                    onValueChange = { viewModel.updateCompression(compression.copy(imageQuality = it)) },
                )
            }
        }

        SectionDivider()

        StorageLimitSection(
            storageLimitGb = settings.storageLimitGb,
            repoSize = repoSize,
            onLimitChange = { viewModel.updateStorageLimit(it) },
        )
    }
}

@Composable
private fun StorageLimitSection(
    storageLimitGb: Int,
    repoSize: RepoSize,
    onLimitChange: (Int) -> Unit,
) {
    var limitText by remember { mutableStateOf("") }
    LaunchedEffect(storageLimitGb) {
        if (limitText.isEmpty() && storageLimitGb > 0) {
            limitText = storageLimitGb.toString()
        }
    }
    Section(title = stringResource(R.string.schedule_storage_limit_title)) {
        Text(
            stringResource(R.string.schedule_storage_limit_body),
            style = MaterialTheme.typography.bodySmall,
        )
        val currentText = when (repoSize) {
            RepoSize.Loading -> stringResource(R.string.schedule_storage_limit_reading)
            RepoSize.Unavailable -> stringResource(R.string.schedule_storage_limit_unavailable)
            is RepoSize.Known -> stringResource(R.string.schedule_storage_limit_current, formatBytes(repoSize.bytes))
        }
        Text(currentText, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = limitText,
            onValueChange = { text ->
                val digits = text.filter { it.isDigit() }.take(6)
                limitText = digits
                onLimitChange(digits.toIntOrNull() ?: 0)
            },
            label = { Text(stringResource(R.string.schedule_storage_limit_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (storageLimitGb <= 0) {
            Text(
                stringResource(R.string.schedule_storage_limit_none),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SteppedValueRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Text(label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step) - 1,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { entered ->
                val digits = entered.filter { it.isDigit() }.take(6)
                text = digits
                val parsed = digits.toIntOrNull()
                if (parsed != null && parsed in range) onValueChange(parsed)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(112.dp),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
