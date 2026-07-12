package io.github.xsaveopt.cryptsync.data.settings

import androidx.annotation.StringRes
import io.github.xsaveopt.cryptsync.R

enum class VideoCodec(@StringRes val labelRes: Int) {
    HEVC(R.string.codec_hevc),
    AV1(R.string.codec_av1),
}

enum class ImageFormat(@StringRes val labelRes: Int) {
    HEIC(R.string.image_format_heic),
}

enum class ScheduleFrequency(@StringRes val labelRes: Int) {
    MANUAL(R.string.frequency_manual),
    HOURLY(R.string.frequency_hourly),
    DAILY(R.string.frequency_daily),
    WEEKLY(R.string.frequency_weekly),
    MONTHLY(R.string.frequency_monthly),
}

enum class NetworkPolicy(@StringRes val labelRes: Int) {
    UNMETERED(R.string.network_unmetered),
    ANY(R.string.network_any),
}

data class CompressionSettings(
    val reencodeMedia: Boolean = true,
    val videoCodec: VideoCodec = VideoCodec.HEVC,
    val videoBitrateKbps: Int = 4000,
    val audioBitrateKbps: Int = 96,
    val imageFormat: ImageFormat = ImageFormat.HEIC,
    val imageQuality: Int = 60,
)

data class ScheduleSettings(
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val hourOfDay: Int = 3,
    val chargingOnly: Boolean = true,
    val networkPolicy: NetworkPolicy = NetworkPolicy.UNMETERED,
)

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val repositoryInitialized: Boolean = false,
    val backupLocations: Set<String> = emptySet(),
    val fullFileAccessRequested: Boolean = false,
    val storageLimitGb: Int = 0,
    val overLimitBytes: Long = 0,
    val compression: CompressionSettings = CompressionSettings(),
    val schedule: ScheduleSettings = ScheduleSettings(),
)
