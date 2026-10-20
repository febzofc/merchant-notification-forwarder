package com.example

/**
 * Menyaring notifikasi yang sama agar tidak diteruskan berulang kali.
 *
 * Android memanggil [android.service.notification.NotificationListenerService.onNotificationPosted]
 * setiap kali sebuah notifikasi dibuat **atau diperbarui**, sehingga satu notifikasi
 * pembayaran dapat terkirim berkali-kali ke webhook. Guard ini mengingat kunci
 * notifikasi yang baru saja diteruskan dan mengabaikannya selama [windowMs].
 *
 * Kelas ini murni Kotlin (tanpa dependensi Android) agar mudah diuji.
 */
class DuplicateGuard(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val lastSeen = LinkedHashMap<String, Long>()

    /**
     * @return `true` jika [key] sudah diteruskan dalam [windowMs] terakhir.
     */
    fun isDuplicate(key: String): Boolean {
        val now = clock()
        synchronized(lastSeen) {
            val previous = lastSeen[key]
            if (previous != null && now - previous in 0 until windowMs) {
                // Geser jendela: notifikasi yang terus diperbarui tidak boleh
                // lolos hanya karena jendela pertama habis.
                lastSeen[key] = now
                return true
            }

            lastSeen[key] = now
            evictStale(now)
            return false
        }
    }

    fun clear() {
        synchronized(lastSeen) { lastSeen.clear() }
    }

    private fun evictStale(now: Long) {
        val iterator = lastSeen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value >= windowMs) iterator.remove()
        }
        while (lastSeen.size > maxEntries) {
            val oldest = lastSeen.keys.firstOrNull() ?: break
            lastSeen.remove(oldest)
        }
    }

    companion object {
        /** Jendela deduplikasi default: 15 detik. */
        const val DEFAULT_WINDOW_MS = 15_000L
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
