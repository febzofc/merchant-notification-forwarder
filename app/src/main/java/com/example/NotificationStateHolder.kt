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
    private const val PREFS_NAME = "bayarin_prefs"
    private const val OLD_PREFS_NAME = "gopay_forwarder_prefs"
    private const val KEY_LOGS = "notification_logs"
    private const val KEY_WEBHOOK_URL = "webhook_url"
    private const val KEY_FILTER_MODE = "filter_mode" // "qris" (GoPay & DANA), "all_qris", "all", "custom"
    private const val KEY_CUSTOM_PACKAGE = "custom_package"
    private const val KEY_BLOCK_PROMOS = "block_promos"
    private const val KEY_REQUIRE_AMOUNT = "require_amount"

    val recentLogs = MutableStateFlow<List<ForwardLog>>(emptyList())
    val webhookUrl = MutableStateFlow("")
    val filterMode = MutableStateFlow("qris")
    val customPackage = MutableStateFlow("")
    val blockPromos = MutableStateFlow(true)
    val requireAmount = MutableStateFlow(true)

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldPrefs = appContext.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)

        val savedUrl = prefs.getString(KEY_WEBHOOK_URL, null)
            ?: oldPrefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        val savedMode = prefs.getString(KEY_FILTER_MODE, null)
            ?: oldPrefs.getString(KEY_FILTER_MODE, "qris") ?: "qris"
        val savedCustom = prefs.getString(KEY_CUSTOM_PACKAGE, null)
            ?: oldPrefs.getString(KEY_CUSTOM_PACKAGE, "") ?: ""
        val savedBlockPromos = prefs.getBoolean(KEY_BLOCK_PROMOS, true)
        val savedRequireAmount = prefs.getBoolean(KEY_REQUIRE_AMOUNT, true)

        webhookUrl.value = savedUrl
        filterMode.value = if (savedMode == "gopay") "qris" else savedMode
        customPackage.value = savedCustom
        blockPromos.value = savedBlockPromos
        requireAmount.value = savedRequireAmount

        if (!prefs.contains(KEY_WEBHOOK_URL) && savedUrl.isNotEmpty()) {
            prefs.edit()
                .putString(KEY_WEBHOOK_URL, savedUrl)
                .putString(KEY_FILTER_MODE, filterMode.value)
                .putString(KEY_CUSTOM_PACKAGE, savedCustom)
                .putBoolean(KEY_BLOCK_PROMOS, savedBlockPromos)
                .putBoolean(KEY_REQUIRE_AMOUNT, savedRequireAmount)
                .apply()
        }

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

    fun saveBlockPromos(enabled: Boolean) {
        blockPromos.value = enabled
        prefs.edit().putBoolean(KEY_BLOCK_PROMOS, enabled).apply()
    }

    fun saveRequireAmount(enabled: Boolean) {
        requireAmount.value = enabled
        prefs.edit().putBoolean(KEY_REQUIRE_AMOUNT, enabled).apply()
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
