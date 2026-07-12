package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ForwardLog(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val status: String, // "SUCCESS", "FAILED", "SENDING"
    val responseCode: Int? = null,
    val error: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("packageName", packageName)
            put("title", title)
            put("text", text)
            put("status", status)
            put("responseCode", responseCode ?: -1)
            put("error", error ?: "")
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): ForwardLog {
            val responseCodeVal = obj.optInt("responseCode", -1)
            return ForwardLog(
                id = obj.optString("id", ""),
                timestamp = obj.optLong("timestamp", 0L),
                packageName = obj.optString("packageName", ""),
                title = obj.optString("title", ""),
                text = obj.optString("text", ""),
                status = obj.optString("status", "FAILED"),
                responseCode = if (responseCodeVal == -1) null else responseCodeVal,
                error = obj.optString("error", "").let { if (it.isEmpty()) null else it }
            )
        }
    }
}

object NotificationStateHolder {
    private const val PREFS_NAME = "gopay_forwarder_prefs"
    private const val KEY_LOGS = "notification_logs"
    private const val KEY_WEBHOOK_URL = "webhook_url"
    private const val KEY_FILTER_MODE = "filter_mode" // "gopay", "all", "custom"
    private const val KEY_CUSTOM_PACKAGE = "custom_package"

    val recentLogs = MutableStateFlow<List<ForwardLog>>(emptyList())
    val webhookUrl = MutableStateFlow("")
    val filterMode = MutableStateFlow("gopay")
    val customPackage = MutableStateFlow("")

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        webhookUrl.value = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        filterMode.value = prefs.getString(KEY_FILTER_MODE, "gopay") ?: "gopay"
        customPackage.value = prefs.getString(KEY_CUSTOM_PACKAGE, "") ?: ""
        loadLogs()
    }

    fun saveWebhookUrl(url: String) {
        webhookUrl.value = url
        prefs.edit().putString(KEY_WEBHOOK_URL, url).apply()
    }

    fun saveFilterMode(mode: String) {
        filterMode.value = mode
        prefs.edit().putString(KEY_FILTER_MODE, mode).apply()
    }

    fun saveCustomPackage(pkg: String) {
        customPackage.value = pkg
        prefs.edit().putString(KEY_CUSTOM_PACKAGE, pkg).apply()
    }

    @Synchronized
    fun addLog(log: ForwardLog) {
        val current = recentLogs.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == log.id }
        if (existingIndex >= 0) {
            current[existingIndex] = log
        } else {
            current.add(0, log)
        }
        
        // Keep last 30 logs to stay extremely light
        while (current.size > 30) {
            current.removeAt(current.lastIndex)
        }
        recentLogs.value = current
        saveLogs()
    }

    @Synchronized
    private fun saveLogs() {
        try {
            val arr = JSONArray()
            recentLogs.value.forEach {
                arr.put(it.toJsonObject())
            }
            prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLogs() {
        try {
            val jsonStr = prefs.getString(KEY_LOGS, null) ?: return
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ForwardLog>()
            for (i in 0 until arr.length()) {
                list.add(ForwardLog.fromJsonObject(arr.getJSONObject(i)))
            }
            recentLogs.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearLogs() {
        recentLogs.value = emptyList()
        prefs.edit().remove(KEY_LOGS).apply()
    }
}
