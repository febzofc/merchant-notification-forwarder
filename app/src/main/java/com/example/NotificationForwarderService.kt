package com.example

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NotificationForwarderService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationForwarder", "Service created")
        NotificationStateHolder.init(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("NotificationForwarder", "Service destroyed")
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        
        // Load configuration
        val filterMode = NotificationStateHolder.filterMode.value
        val customPackage = NotificationStateHolder.customPackage.value
        val webhookUrl = NotificationStateHolder.webhookUrl.value

        if (webhookUrl.isBlank()) {
            Log.w("NotificationForwarder", "Webhook URL is empty. Skipping notification forwarding.")
            return
        }

        if (!shouldForward(packageName, filterMode, customPackage)) {
            Log.d("NotificationForwarder", "Package $packageName filtered out by mode $filterMode")
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // If title and text are both blank, skip
        if (title.isBlank() && text.isBlank()) {
            return
        }

        val uniqueId = "${sbn.packageName}_${sbn.id}_${sbn.postTime}"

        // Create log item in SENDING status
        val logItem = ForwardLog(
            id = uniqueId,
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            title = title,
            text = text,
            status = "SENDING"
        )
        NotificationStateHolder.addLog(logItem)

        // Forward to Webhook in background
        serviceScope.launch {
            try {
                val json = JSONObject().apply {
                    put("id", sbn.id)
                    put("uniqueId", uniqueId)
                    put("packageName", packageName)
                    put("title", title)
                    put("text", text)
                    put("subText", subText)
                    put("postTime", sbn.postTime)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        NotificationStateHolder.addLog(
                            logItem.copy(
                                status = "SUCCESS",
                                responseCode = response.code
                            )
                        )
                        Log.d("NotificationForwarder", "Successfully forwarded notification from $packageName to webhook")
                    } else {
                        val responseBody = response.body?.string()?.take(200) ?: ""
                        NotificationStateHolder.addLog(
                            logItem.copy(
                                status = "FAILED",
                                responseCode = response.code,
                                error = "HTTP ${response.code}: $responseBody"
                            )
                        )
                        Log.e("NotificationForwarder", "Failed forwarding: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                NotificationStateHolder.addLog(
                    logItem.copy(
                        status = "FAILED",
                        error = e.localizedMessage ?: e.message ?: "Unknown Connection Error"
                    )
                )
                Log.e("NotificationForwarder", "Error forwarding notification", e)
            }
        }
    }

    private fun shouldForward(packageName: String, mode: String, customFilter: String): Boolean {
        return when (mode) {
            "all" -> true
            "gopay" -> {
                packageName.contains("gojek.gobiz", ignoreCase = true) ||
                packageName.contains("gopay", ignoreCase = true) ||
                packageName.contains("gopay.merchant", ignoreCase = true)
            }
            "custom" -> {
                packageName.equals(customFilter.trim(), ignoreCase = true)
            }
            else -> false
        }
    }
}
