package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_SERVICE_ID = "bayarin_service_channel"
    const val CHANNEL_ALERT_ID = "bayarin_alert_channel"

    /** ID notifikasi konfirmasi; tetap agar tidak menumpuk di notification shade. */
    private const val ALERT_NOTIFICATION_ID = 2002

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Status Layanan Bayarin",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status aktif listener pemantau transaksi QRIS"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Notifikasi Transaksi QRIS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat pembayaran QRIS DANA / GoPay berhasil diterima dan diteruskan"
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun sendTestNotification(context: Context): Boolean {
        initChannels(context)
        return try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Bayarin: Notifikasi Aktif!")
                .setContentText("Izin notifikasi berhasil aktif. Bayarin siap memantau transaksi QRIS DANA & GoPay.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val manager = NotificationManagerCompat.from(context)
            manager.notify(1001, builder.build())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun sendForwardNotification(context: Context, provider: String, title: String, text: String) {
        initChannels(context)
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("[$provider] Pembayaran Masuk Diteruskan")
                .setContentText(if (text.isNotBlank()) text else title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val manager = NotificationManagerCompat.from(context)
            // ID tetap: notifikasi baru menggantikan yang lama, bukan menumpuk
            // di shade setiap kali pembayaran diteruskan.
            manager.notify(ALERT_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
