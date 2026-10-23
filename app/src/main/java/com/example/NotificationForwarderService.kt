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

    /**
     * Mencegah satu notifikasi diteruskan berkali-kali. `onNotificationPosted`
     * dipanggil ulang setiap notifikasi diperbarui, dan notifikasi konfirmasi
     * milik aplikasi ini juga tertangkap oleh listener.
     */
    private val duplicateGuard = DuplicateGuard()

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

        // Notifikasi milik Bayarin sendiri WAJIB diabaikan. Notifikasi
        // "Pembayaran Masuk Diteruskan" berisi teks pembayaran asli, sehingga
        // tanpa guard ini listener akan menangkapnya lagi dan meneruskannya
        // tanpa henti (loop tak berujung ke webhook).
        if (packageName == this.packageName) {
            Log.d("NotificationForwarder", "Mengabaikan notifikasi milik sendiri")
            return
        }

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

        val blockPromos = NotificationStateHolder.blockPromos.value
        val requireAmount = NotificationStateHolder.requireAmount.value
        val provider = detectProvider(packageName, title, text)
        val amount = extractAmount("$title $text $subText")

        val decision = evaluateFilter(
            packageName = packageName,
            title = title,
            text = text,
            subText = subText,
            mode = filterMode,
            customPackage = customPackage,
            blockPromos = blockPromos,
            requireAmount = requireAmount,
            amount = amount,
            provider = provider
        )

        if (!decision.allowed) {
            Log.d("NotificationForwarder", "Package $packageName filtered out: ${decision.reason}")
            return
        }

        val uniqueId = "${sbn.packageName}_${sbn.id}_${sbn.postTime}"

        // Deduplikasi: satu notifikasi pembayaran hanya boleh diteruskan sekali
        // per jendela waktu, meskipun sistem memanggil onNotificationPosted
        // berkali-kali karena notifikasi diperbarui.
        val dedupeKey = "$packageName|${sbn.id}|$title|$text|$subText|${amount ?: -1L}"
        if (duplicateGuard.isDuplicate(dedupeKey)) {
            Log.d("NotificationForwarder", "Notifikasi duplikat diabaikan untuk $packageName")
            return
        }

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
                    .header("User-Agent", "Bayarin-Forwarder/${BuildConfig.VERSION_NAME}")
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

    companion object {
        fun extractAmount(text: String): Long? {
            return try {
                val regex = Regex("""(?:Rp\.?|IDR)\s*([0-9.,]+)""", RegexOption.IGNORE_CASE)
                val match = regex.find(text) ?: return null
                var numStr = match.groupValues[1].trim()
                numStr = numStr.replace(Regex("[,.]00$"), "")
                val cleaned = numStr.replace(".", "").replace(",", "")
                cleaned.toLongOrNull()
            } catch (e: Exception) {
                null
            }
        }

        fun detectProvider(pkg: String, title: String, text: String): String {
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

        /**
         * Paket aplikasi pembayaran merchant yang didukung.
         * Pencocokan dilakukan pada package name, bukan pada isi teks, karena
         * kata "dana" di dalam teks berarti "funds" dan membuat notifikasi
         * transfer bank biasa ikut terdeteksi.
         */
        private val MERCHANT_PACKAGES = listOf(
            "id.dana",              // DANA / DANA Bisnis
            "com.gojek.gobiz",      // GoBiz (GoPay Merchant)
            "com.gojek.gopay",      // GoPay Merchant
            "com.gojek.merchant",   // GoPay Merchant (varian lain)
            "id.co.ovo"             // OVO / OVO Merchant
        )

        /** Paket tambahan untuk mode "Semua E-Wallet QRIS". */
        private val ALL_QRIS_PACKAGES = MERCHANT_PACKAGES + listOf(
            "com.gojek.app",              // Gojek (GoPay)
            "com.shopeepay.id",
            "com.shopee.id",
            "com.telkomsel.telkomselcm",  // LinkAja
            "id.co.linkaja",
            "com.bca",
            "id.bmri.livin",
            "id.co.bri.brimo"
        )

        /** Kata yang menandakan transfer pribadi, bukan pembayaran merchant. */
        private val TRANSFER_KEYWORDS = listOf(
            "transfer", "dikirim", "kirim uang", "menerima uang", "uang masuk",
            "top up", "isi saldo", "tarik tunai", "tarik saldo",
            "refund", "pengembalian", "dana kaget", "minta uang"
        )

        /** Kata yang menandakan notifikasi pembayaran merchant. */
        private val PAYMENT_KEYWORDS = listOf(
            "qris", "pembayaran", "payment", "transaksi", "transaction",
            "penjualan", "merchant", "pelanggan", "dibayar", "berhasil dibayar"
        )

        private fun matchesPackage(pkg: String, candidates: List<String>): Boolean =
            candidates.any { pkg == it || pkg.startsWith("$it.") }

        fun evaluateFilter(
            packageName: String,
            title: String,
            text: String,
            subText: String = "",
            mode: String,
            customPackage: String = "",
            blockPromos: Boolean = true,
            requireAmount: Boolean = true,
            amount: Long? = null,
            provider: String = ""
        ): FilterDecision {
            val combined = "$packageName $title $text $subText".lowercase()
            val pkg = packageName.lowercase()

            val isTargetApp = when (mode) {
                "all" -> true
                // "qris" dan "gopay" adalah nilai lama; arahkan ke mode merchant.
                "qris", "gopay", "merchant" -> matchesPackage(pkg, MERCHANT_PACKAGES)
                "all_qris" -> matchesPackage(pkg, ALL_QRIS_PACKAGES)
                "custom" -> pkg == customPackage.trim().lowercase()
                else -> false
            }

            if (!isTargetApp) {
                return FilterDecision(false, "Paket aplikasi tidak sesuai filter mode ($mode)")
            }

            if (blockPromos) {
                val promoKeywords = listOf(
                    "promo", "diskon", "cashback", "voucher", "kupon", "hadiah", "lucky",
                    "spin", "game", "invite", "ajak", "poin", "coin", "mission", "misi",
                    "upgrade", "pinjaman", "cicil", "paylater", "chat", "pesan",
                    "spesial", "khusus kamu", "klaim", "ajak teman"
                )
                if (promoKeywords.any { combined.contains(it) }) {
                    val isMarketingMessage = combined.contains("cashback") ||
                            combined.contains("voucher") ||
                            combined.contains("klaim") ||
                            combined.contains("amplop") ||
                            combined.contains("promo") ||
                            combined.contains("diskon")
                    val isRealIncoming = combined.contains("berhasil menerima") ||
                            combined.contains("berhasil diterima") ||
                            combined.contains("pembayaran qris") ||
                            (combined.contains("transfer masuk") && !combined.contains("voucher"))
                    if (isMarketingMessage || !isRealIncoming) {
                        return FilterDecision(false, "Notifikasi terdeteksi promo / spam")
                    }
                }
            }

            if (mode != "all") {
                val hasQris = combined.contains("qris")
                val isTransfer = TRANSFER_KEYWORDS.any { combined.contains(it) }

                // Transfer pribadi / uang masuk biasa tidak boleh dianggap pembayaran.
                if (!hasQris && isTransfer) {
                    return FilterDecision(false, "Transfer pribadi, bukan pembayaran QRIS merchant")
                }

                val hasPaymentEvidence = hasQris || PAYMENT_KEYWORDS.any { combined.contains(it) }
                if (!hasPaymentEvidence) {
                    return FilterDecision(false, "Bukan notifikasi pembayaran merchant")
                }
            }

            val resolvedAmount = amount ?: extractAmount("$title $text $subText")
            if (requireAmount && (resolvedAmount == null || resolvedAmount <= 0L)) {
                return FilterDecision(false, "Tidak terdeteksi nominal pembayaran yang valid")
            }

            return FilterDecision(true, "Notifikasi valid")
        }
    }
}

data class FilterDecision(
    val allowed: Boolean,
    val reason: String = ""
)
