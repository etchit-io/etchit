package com.autonomi.antpaste

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Manages the keep-alive foreground service, wake lock, and wallet
 * approval notifications for long-running operations. Starting an
 * operation launches [KeepAliveService] so Android/Samsung won't kill
 * network connections during multi-minute quote collection.
 */
class OperationHelper(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager = NotificationManagerCompat.from(context)
    private var serviceRunning = false

    init {
        createUrgentChannel()
    }

    private fun createUrgentChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Channel attributes are immutable after first create on most
            // OEM launchers (Samsung One UI in particular). Always delete
            // the prior channel id before recreating with new config.
            mgr.deleteNotificationChannel(LEGACY_CHANNEL_URGENT_ID)
            val channel = NotificationChannel(
                CHANNEL_URGENT_ID, "Wallet Approval", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when wallet approval is needed"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    /** Start the foreground service and acquire the wake lock. */
    fun start(text: String = "Working…") {
        acquireWakeLock()
        val intent = Intent(context, KeepAliveService::class.java).apply {
            putExtra(KeepAliveService.EXTRA_TEXT, text)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        serviceRunning = true
    }

    /** Update the foreground service notification text. */
    fun updateProgress(lifecycle: LifecycleOwner, text: String) {
        if (!serviceRunning) {
            start(text)
            return
        }
        // Refresh the wake-lock deadline on every progress event so a
        // long etch doesn't outlast a single 10-minute window.
        acquireWakeLock()
        val intent = Intent(context, KeepAliveService::class.java).apply {
            action = KeepAliveService.ACTION_UPDATE
            putExtra(KeepAliveService.EXTRA_TEXT, text)
        }
        context.startService(intent)
    }

    /**
     * Alert the user that quote collection is done and the cost prompt
     * is on screen. `setFullScreenIntent` is what wakes the device when
     * locked — Samsung One UI defers HIGH-importance notifications past
     * unlock without it. Respects system volume / silent / DND.
     */
    fun notifyQuotesReady() {
        vibrate()
        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Quotes ready")
            .setContentText("Tap to confirm cost in etchit")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .setFullScreenIntent(launchIntent(), true)
            .build()
        try {
            notificationManager.notify(NOTIFICATION_READY_ID, notification)
        } catch (_: SecurityException) { }
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
            // No vibrator permission or hardware — silent fall-through.
        }
    }

    /**
     * Post a high-priority notification when the wallet needs approval.
     * Call this *before* dispatching the wallet request that deep-links
     * to the wallet app. Android suppresses heads-up while we're RESUMED,
     * so the alert only surfaces once the wallet app takes over — which
     * is exactly when the user needs the path back.
     */
    fun notifyApprovalNeeded() {
        vibrate()
        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Wallet approval needed")
            .setContentText("Open your wallet to approve the transaction")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .setFullScreenIntent(launchIntent(), true)
            .build()
        try {
            notificationManager.notify(NOTIFICATION_APPROVAL_ID, notification)
        } catch (_: SecurityException) { }
    }

    /** Stop the foreground service, release wake lock, clear notifications. */
    fun finish() {
        releaseWakeLock()
        if (serviceRunning) {
            context.stopService(Intent(context, KeepAliveService::class.java))
            serviceRunning = false
        }
        notificationManager.cancel(NOTIFICATION_APPROVAL_ID)
        notificationManager.cancel(NOTIFICATION_READY_ID)
    }

    private fun acquireWakeLock() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = wakeLock ?: pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "etchit:operation"
        ).also {
            // Without this, repeated acquire() calls increment a refcount
            // and stack delayed releasers — the lock then stays held for
            // up to 10 min past the user-visible "operation done" moment
            // because the single release() in finish() only decrements
            // once. setReferenceCounted(false) makes acquire(timeout)
            // genuinely idempotent: deadline resets, count stays at 1.
            it.setReferenceCounted(false)
            wakeLock = it
        }
        try {
            lock.acquire(10 * 60 * 1000L)
        } catch (e: SecurityException) {
            Log.w("ant-paste", "OperationHelper: wake-lock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun launchIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_URGENT_ID = "wallet_approval_v2"
        private const val LEGACY_CHANNEL_URGENT_ID = "wallet_approval"
        private const val NOTIFICATION_APPROVAL_ID = 1002
        private const val NOTIFICATION_READY_ID = 1003
    }
}
