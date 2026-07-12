package io.github.xsaveopt.cryptsync.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "cryptsync_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            onboardingComplete = prefs[Keys.ONBOARDING] ?: false,
            repositoryInitialized = prefs[Keys.REPO_INIT] ?: false,
            backupLocations = prefs[Keys.BACKUP_LOCATIONS]
                ?: ((prefs[Keys.MEDIA_DIRS] ?: emptySet()) + (prefs[Keys.EXTRA_PATHS] ?: emptySet())),
            fullFileAccessRequested = prefs[Keys.FULL_ACCESS] ?: false,
            storageLimitGb = prefs[Keys.STORAGE_LIMIT] ?: 0,
            overLimitBytes = prefs[Keys.OVER_LIMIT] ?: 0L,
            compression = CompressionSettings(
                reencodeMedia = prefs[Keys.REENCODE_MEDIA] ?: true,
                videoCodec = enumOrDefault(prefs[Keys.VIDEO_CODEC], VideoCodec.HEVC),
                videoBitrateKbps = prefs[Keys.VIDEO_BITRATE] ?: 4000,
                audioBitrateKbps = prefs[Keys.AUDIO_BITRATE] ?: 96,
                imageFormat = enumOrDefault(prefs[Keys.IMAGE_FORMAT], ImageFormat.HEIC),
                imageQuality = prefs[Keys.IMAGE_QUALITY] ?: 60,
            ),
            schedule = ScheduleSettings(
                frequency = enumOrDefault(prefs[Keys.FREQUENCY], ScheduleFrequency.DAILY),
                hourOfDay = prefs[Keys.HOUR] ?: 3,
                chargingOnly = prefs[Keys.CHARGING_ONLY] ?: true,
                networkPolicy = enumOrDefault(prefs[Keys.NETWORK], NetworkPolicy.UNMETERED),
            ),
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.ONBOARDING] = value }
    suspend fun setRepositoryInitialized(value: Boolean) = edit { it[Keys.REPO_INIT] = value }
    suspend fun setBackupLocations(locations: Set<String>) = edit { it[Keys.BACKUP_LOCATIONS] = locations }
    suspend fun setFullFileAccessRequested(value: Boolean) = edit { it[Keys.FULL_ACCESS] = value }
    suspend fun setStorageLimit(gb: Int) = edit {
        it[Keys.STORAGE_LIMIT] = gb.coerceAtLeast(0)
        it[Keys.OVER_LIMIT] = 0L
    }

    suspend fun setOverLimitBytes(bytes: Long) = edit { it[Keys.OVER_LIMIT] = bytes.coerceAtLeast(0) }

    suspend fun setCompression(settings: CompressionSettings) = edit {
        it[Keys.REENCODE_MEDIA] = settings.reencodeMedia
        it[Keys.VIDEO_CODEC] = settings.videoCodec.name
        it[Keys.VIDEO_BITRATE] = settings.videoBitrateKbps
        it[Keys.AUDIO_BITRATE] = settings.audioBitrateKbps
        it[Keys.IMAGE_FORMAT] = settings.imageFormat.name
        it[Keys.IMAGE_QUALITY] = settings.imageQuality
    }

    suspend fun setSchedule(settings: ScheduleSettings) = edit {
        it[Keys.FREQUENCY] = settings.frequency.name
        it[Keys.HOUR] = settings.hourOfDay
        it[Keys.CHARGING_ONLY] = settings.chargingOnly
        it[Keys.NETWORK] = settings.networkPolicy.name
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val REPO_INIT = booleanPreferencesKey("repository_initialized")
        val BACKUP_LOCATIONS = stringSetPreferencesKey("backup_locations")
        val MEDIA_DIRS = stringSetPreferencesKey("media_directories")
        val EXTRA_PATHS = stringSetPreferencesKey("extra_paths")
        val FULL_ACCESS = booleanPreferencesKey("full_file_access_requested")
        val STORAGE_LIMIT = intPreferencesKey("storage_limit_gb")
        val OVER_LIMIT = longPreferencesKey("over_limit_bytes")
        val REENCODE_MEDIA = booleanPreferencesKey("reencode_media")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val IMAGE_FORMAT = stringPreferencesKey("image_format")
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val FREQUENCY = stringPreferencesKey("schedule_frequency")
        val HOUR = intPreferencesKey("schedule_hour")
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        val NETWORK = stringPreferencesKey("network_policy")
    }
}
