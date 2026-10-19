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

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // If title and text are both blank, skip
        if (title.isBlank() && text.isBlank()) {
            return
        }

        if (!shouldForward(packageName, title, text, filterMode, customPackage)) {
            Log.d("NotificationForwarder", "Package $packageName filtered out by mode $filterMode")
            return
        }

        val uniqueId = "${sbn.packageName}_${sbn.id}_${sbn.postTime}"
        val provider = detectProvider(packageName, title, text)
        val amount = extractAmount("$title $text")

        // Create log item in SENDING status
        val logItem = ForwardLog(
            id = uniqueId,
            timestamp = System.currentTimeMillis(),
            packageName = if (provider != "OTHER") "[$provider] $packageName" else packageName,
            title = title,
            text = text,
            status = "SENDING"
        )
        NotificationStateHolder.addLog(logItem)

        // Forward to Webhook in background
        serviceScope.launch {
            try {
                val json = JSONObject().apply {
                    put("app", "Bayarin")
                    put("id", sbn.id)
                    put("uniqueId", uniqueId)
                    put("provider", provider)
                    put("packageName", packageName)
                    put("title", title)
                    put("text", text)
                    put("message", text)
                    put("rawText", "$title: $text")
                    if (amount != null) {
                        put("amount", amount)
                        put("nominal", amount)
                    }
                    put("subText", subText)
                    put("postTime", sbn.postTime)
                    put("timestamp", System.currentTimeMillis())
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .header("User-Agent", "Bayarin-Forwarder/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()?.take(250) ?: ""
                    if (response.isSuccessful) {
                        NotificationStateHolder.addLog(
                            logItem.copy(
                                status = "SUCCESS",
                                responseCode = response.code,
                                text = if (responseBody.isNotEmpty()) "$text\n-> $responseBody" else text
                            )
                        )
                        Log.d("Bayarin", "Successfully forwarded notification from $packageName to webhook")
                        NotificationHelper.sendForwardNotification(
                            this@NotificationForwarderService,
                            provider,
                            title,
                            text
                        )
                    } else {
                        NotificationStateHolder.addLog(
                            logItem.copy(
                                status = "FAILED",
                                responseCode = response.code,
                                error = "HTTP ${response.code}: $responseBody"
                            )
                        )
                        Log.e("Bayarin", "Failed forwarding: HTTP ${response.code} - $responseBody")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "Unknown Connection Error"
                NotificationStateHolder.addLog(
                    logItem.copy(
                        status = "FAILED",
                        error = errorMsg
                    )
                )
                Log.e("Bayarin", "Error forwarding notification: $errorMsg", e)
            }
        }
    }

    private fun extractAmount(text: String): Long? {
        return try {
            val regex = Regex("""(?:Rp|IDR)\s*([0-9.,]+)""", RegexOption.IGNORE_CASE)
            val match = regex.find(text) ?: return null
            val numStr = match.groupValues[1].replace(".", "").replace(",", "")
            numStr.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun detectProvider(pkg: String, title: String, text: String): String {
        val combined = "$pkg $title $text".lowercase()
        return when {
            pkg.contains("dana") || combined.contains("dana") -> "DANA"
            pkg.contains("gopay") || pkg.contains("gobiz") || pkg.contains("gojek") || combined.contains("gopay") || combined.contains("gobiz") -> "GOPAY"
            pkg.contains("ovo") || combined.contains("ovo") -> "OVO"
            pkg.contains("shopee") || combined.contains("shopeepay") -> "SHOPEEPAY"
            pkg.contains("linkaja") || combined.contains("linkaja") -> "LINKAJA"
            combined.contains("qris") -> "QRIS"
            else -> "OTHER"
        }
    }

    private fun isActualPaymentNotification(title: String, text: String): Boolean {
        val combined = "$title $text".lowercase()

        // Reject spam / promo / marketing / chat notifications
        val promoKeywords = listOf(
            "promo", "diskon", "cashback", "voucher", "kupon", "hadiah", "lucky",
            "spin", "game", "invite", "ajak", "poin", "coin", "mission", "misi",
            "upgrade", "pinjaman", "cicil", "paylater", "chat", "pesan",
            "spesial", "khusus kamu", "klaim", "ajak teman"
        )
        if (promoKeywords.any { combined.contains(it) }) {
            val hasStrongPayment = combined.contains("berhasil") || combined.contains("menerima") || combined.contains("diterima") || combined.contains("qris")
            if (!hasStrongPayment) return false
        }

        // Must contain payment / incoming transaction indicators AND valid amount
        val paymentKeywords = listOf(
            "berhasil", "menerima", "diterima", "terima", "masuk", "paid",
            "received", "pembayaran", "transaksi", "sukses", "qris", "rp", "idr"
        )
        val hasPaymentKeyword = paymentKeywords.any { combined.contains(it) }
        val amount = extractAmount(combined)

        return hasPaymentKeyword && amount != null && amount > 0L
    }

    private fun shouldForward(packageName: String, title: String, text: String, mode: String, customFilter: String): Boolean {
        val combined = "$packageName $title $text".lowercase()
        val isPayment = isActualPaymentNotification(title, text)

        return when (mode) {
            "all" -> true // In all mode, we still filter out obvious promos if desired, or allow all. Let's still require payment for financial apps or allow all if user chose all. Wait, if mode is "all", user wants everything. But for "qris", "gopay", "merchant", "all_qris", we strictly require isPayment.
            "qris", "gopay", "merchant" -> {
                val isTargetApp = packageName.contains("dana", ignoreCase = true) ||
                    packageName.contains("gojek.gobiz", ignoreCase = true) ||
                    packageName.contains("gopay", ignoreCase = true) ||
                    packageName.contains("gojek.app", ignoreCase = true) ||
                    combined.contains("dana") ||
                    combined.contains("gopay") ||
                    combined.contains("gobiz") ||
                    combined.contains("qris")
                isTargetApp && isPayment
            }
            "all_qris" -> {
                val isTargetApp = packageName.contains("dana", ignoreCase = true) ||
                    packageName.contains("gopay", ignoreCase = true) ||
                    packageName.contains("gobiz", ignoreCase = true) ||
                    packageName.contains("gojek", ignoreCase = true) ||
                    packageName.contains("ovo", ignoreCase = true) ||
                    packageName.contains("shopee", ignoreCase = true) ||
                    packageName.contains("linkaja", ignoreCase = true) ||
                    packageName.contains("bca", ignoreCase = true) ||
                    packageName.contains("bmri", ignoreCase = true) ||
                    packageName.contains("livin", ignoreCase = true) ||
                    combined.contains("qris") ||
                    combined.contains("dana") ||
                    combined.contains("gopay")
                isTargetApp && isPayment
            }
            "custom" -> {
                packageName.equals(customFilter.trim(), ignoreCase = true) && isPayment
            }
            else -> false
        }
    }
}
