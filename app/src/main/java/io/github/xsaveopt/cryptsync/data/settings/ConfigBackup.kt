package io.github.xsaveopt.cryptsync.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.util.AppLogger
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigBackup @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    private val logger: AppLogger,
) {
    private val configFile = File(context.filesDir, FILE_NAME)

    fun file(): File = configFile

    suspend fun export(): File {
        val s = settingsRepository.settings.first()
        val json = JSONObject().apply {
            put("version", VERSION)
            put("backupLocations", JSONArray(s.backupLocations.toList()))
            put("storageLimitGb", s.storageLimitGb)
            put(
                "compression",
                JSONObject().apply {
                    put("reencodeMedia", s.compression.reencodeMedia)
                    put("videoCodec", s.compression.videoCodec.name)
                    put("videoBitrateKbps", s.compression.videoBitrateKbps)
                    put("audioBitrateKbps", s.compression.audioBitrateKbps)
                    put("imageFormat", s.compression.imageFormat.name)
                    put("imageQuality", s.compression.imageQuality)
                },
            )
            put(
                "schedule",
                JSONObject().apply {
                    put("frequency", s.schedule.frequency.name)
                    put("hourOfDay", s.schedule.hourOfDay)
                    put("chargingOnly", s.schedule.chargingOnly)
                    put("networkPolicy", s.schedule.networkPolicy.name)
                },
            )
        }
        configFile.writeText(json.toString())
        return configFile
    }

    fun matches(path: String): Boolean = path == configFile.absolutePath

    suspend fun import(source: File): Boolean = runCatching {
        val json = JSONObject(source.readText())
        if (json.optInt("version", 0) > VERSION) {
            logger.warn(TAG, "Config was written by a newer app version, importing what is readable")
        }

        val locations = (json.stringSet("backupLocations") + json.stringSet("mediaDirectories") + json.stringSet("extraPaths"))
            .filter(ConfigSanitizer::isSafePath)
            .toSet()
        settingsRepository.setBackupLocations(locations)
        settingsRepository.setStorageLimit(json.optInt("storageLimitGb", 0).coerceAtLeast(0))

        json.optJSONObject("compression")?.let { c ->
            settingsRepository.setCompression(
                CompressionSettings(
                    reencodeMedia = c.optBoolean("reencodeMedia", true),
                    videoCodec = ConfigSanitizer.enumOrDefault(c.optString("videoCodec"), VideoCodec.HEVC),
                    videoBitrateKbps = c.optInt("videoBitrateKbps", 4000).coerceIn(500, 100_000),
                    audioBitrateKbps = c.optInt("audioBitrateKbps", 96).coerceIn(16, 512),
                    imageFormat = ConfigSanitizer.enumOrDefault(c.optString("imageFormat"), ImageFormat.HEIC),
                    imageQuality = c.optInt("imageQuality", 60).coerceIn(1, 100),
                ),
            )
        }
        json.optJSONObject("schedule")?.let { sc ->
            settingsRepository.setSchedule(
                ScheduleSettings(
                    frequency = ConfigSanitizer.enumOrDefault(sc.optString("frequency"), ScheduleFrequency.DAILY),
                    hourOfDay = sc.optInt("hourOfDay", 3).coerceIn(0, 23),
                    chargingOnly = sc.optBoolean("chargingOnly", true),
                    networkPolicy = ConfigSanitizer.enumOrDefault(sc.optString("networkPolicy"), NetworkPolicy.UNMETERED),
                ),
            )
        }
        logger.info(TAG, "Restored app config from the backup")
        true
    }.getOrElse {
        logger.error(TAG, "Could not read app config from the backup: ${it.message ?: it.javaClass.simpleName}")
        false
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (i in 0 until array.length()) add(array.getString(i))
        }
    }

    private companion object {
        const val FILE_NAME = "cryptsync-config.json"
        const val VERSION = 1
        const val TAG = "Config"
    }
}
