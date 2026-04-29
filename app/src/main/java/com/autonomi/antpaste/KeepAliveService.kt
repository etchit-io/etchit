package com.autonomi.antpaste

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Minimal foreground service that keeps the process alive during long
 * etch/fetch operations. No logic runs here — the Activity's coroutines
 * do the work. This service's only purpose is to prevent Android and
 * Samsung Freecess from killing network connections (especially the
 * WalletConnect relay WebSocket) while the FFI is collecting quotes.
 *
 * Started by [OperationHelper.start], stopped by [OperationHelper.finish].
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Etch Operations", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Working…"

        when (intent?.action) {
            ACTION_UPDATE -> {
                val notification = buildNotification(text)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> stopSelf()
            else -> startForeground(NOTIFICATION_ID, buildNotification(text))
        }

        // START_STICKY: if Android kills the service for memory, restart
        // it with a null intent so we stay in the foreground while the
        // owning operation coroutine still runs. Pairs with
        // android:stopWithTask="false" in the manifest to survive a
        // recent-apps swipe.
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("etchit")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(launchIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "etch_operations"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TEXT = "text"
        const val ACTION_UPDATE = "update"
        const val ACTION_STOP = "stop"
    }
}
