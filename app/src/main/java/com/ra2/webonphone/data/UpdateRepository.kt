package com.ra2.webonphone.data

import com.ra2.webonphone.BuildConfig

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class UpdateRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PREFS_NAME = "ra2_update_prefs"
        private const val KEY_LAST_CHECK_DATE = "last_check_date"
        private const val KEY_CACHED_VERSION = "cached_version"
        private const val KEY_CACHED_UPDATE_CONTENT = "cached_update_content"
        private const val KEY_CACHED_DOMESTIC_URL = "cached_domestic_url"
        private const val KEY_CACHED_INTERNATIONAL_URL = "cached_international_url"
        private const val KEY_VERSION_FIRST_SEEN_DATE = "version_first_seen_date"
        private const val KEY_DIALOG_DISMISSED_VERSION = "dialog_dismissed_version"
        
        private val UPDATE_API_URL = BuildConfig.UPDATE_API_URL
        private const val FORCE_UPDATE_DAYS = 4 // 第四天开始强制更新
    }

    /**
     * 检查是否需要显示更新对话框
     * @param currentVersion 当前应用版本号
     * @return Pair<是否显示对话框, UpdateInfo?>
     */
    suspend fun checkForUpdate(currentVersion: String): Pair<Boolean, UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val today = getCurrentDate()
            val lastCheckDate = prefs.getString(KEY_LAST_CHECK_DATE, "")
            
            // 判断是否需要重新从网络获取更新信息
            val updateInfo = if (lastCheckDate != today) {
                // 每天第一次启动，从网络获取
                fetchUpdateFromNetwork()?.also { info ->
                    // 缓存更新信息和检查日期
                    saveUpdateInfo(info, today)
                }
            } else {
                // 使用缓存的更新信息
                getCachedUpdateInfo()
            }

            if (updateInfo == null) {
                return@withContext Pair(false, null)
            }

            // 比较版本号
            if (updateInfo.version != currentVersion) {
                // 新版本：重置首次发现日期
                val versionFirstSeenDate = prefs.getString(KEY_VERSION_FIRST_SEEN_DATE, "")
                if (versionFirstSeenDate.isNullOrEmpty() || !versionFirstSeenDate.startsWith(updateInfo.version)) {
                    prefs.edit().putString(KEY_VERSION_FIRST_SEEN_DATE, "${updateInfo.version}:$today").apply()
                }
                
                return@withContext Pair(true, updateInfo)
            }
            // 同版本：已是最新版本，不显示更新对话框
            return@withContext Pair(false, null)
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果网络请求失败，尝试使用缓存
            val cachedInfo = getCachedUpdateInfo()
            if (cachedInfo != null && cachedInfo.version != currentVersion) {
                return@withContext Pair(true, cachedInfo)
            }
            return@withContext Pair(false, null)
        }
    }

    /**
     * 判断对话框是否可以关闭
     * @param version 检测到的版本号
     * @param currentVersion 当前应用版本号
     * @return true表示可以关闭（前3天），false表示不可关闭（第4天起）
     */
    fun canDismissDialog(version: String, currentVersion: String): Boolean {
        val versionFirstSeenDate = prefs.getString(KEY_VERSION_FIRST_SEEN_DATE, "") ?: ""
        
        if (versionFirstSeenDate.isEmpty() || !versionFirstSeenDate.startsWith(version)) {
            // 如果没有记录，默认可关闭
            return true
        }

        val firstSeenDate = versionFirstSeenDate.substringAfter(":")
        val today = getCurrentDate()
        val daysPassed = calculateDaysBetween(firstSeenDate, today)
        
        // 前3天（0, 1, 2）可以关闭，第4天（3）开始不可关闭
        return daysPassed < (FORCE_UPDATE_DAYS - 1)
    }

    /**
     * 从网络获取更新信息
     */
    private fun fetchUpdateFromNetwork(): UpdateInfo? {
        return try {
            val url = URL(UPDATE_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<UpdateInfo>(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存更新信息到本地
     */
    private fun saveUpdateInfo(info: UpdateInfo, checkDate: String) {
        prefs.edit().apply {
            putString(KEY_LAST_CHECK_DATE, checkDate)
            putString(KEY_CACHED_VERSION, info.version)
            putString(KEY_CACHED_UPDATE_CONTENT, info.updateContent)
            putString(KEY_CACHED_DOMESTIC_URL, info.domesticUrl)
            putString(KEY_CACHED_INTERNATIONAL_URL, info.internationalUrl)
            apply()
        }
    }

    /**
     * 获取缓存的更新信息
     */
    private fun getCachedUpdateInfo(): UpdateInfo? {
        val version = prefs.getString(KEY_CACHED_VERSION, null)
        val updateContent = prefs.getString(KEY_CACHED_UPDATE_CONTENT, null)
        val domesticUrl = prefs.getString(KEY_CACHED_DOMESTIC_URL, null)
        val internationalUrl = prefs.getString(KEY_CACHED_INTERNATIONAL_URL, null)

        return if (version != null && updateContent != null && domesticUrl != null && internationalUrl != null) {
            UpdateInfo(version, updateContent, domesticUrl, internationalUrl)
        } else {
            null
        }
    }

    /**
     * 获取当前日期字符串 (yyyy-MM-dd)
     */
    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 计算两个日期之间的天数差
     */
    private fun calculateDaysBetween(startDate: String, endDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val start = sdf.parse(startDate)
            val end = sdf.parse(endDate)
            
            if (start != null && end != null) {
                val diffInMillis = end.time - start.time
                (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
}
