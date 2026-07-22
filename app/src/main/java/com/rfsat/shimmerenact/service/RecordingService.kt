package com.rfsat.shimmerenact.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rfsat.shimmerenact.MainActivity

/**
 * Foreground service keeping the recording session alive while the app is
 * backgrounded or the screen is locked. Declared with the `connectedDevice`
 * foreground service type (the session streams from a connected Bluetooth
 * sensor), satisfying Android 14+ FGS type requirements and Android 16
 * background execution policy.
 *
 * Notification strategy:
 *  - API 36+ (Android 16): platform `Notification.ProgressStyle` in
 *    indeterminate mode — the new progress-centric live notification style.
 *  - Older API levels: `NotificationCompat` with a chronometer bound to the
 *    session start time, so elapsed time ticks without any app-side updates.
 *
 * Row-count updates are pushed periodically by the ViewModel via [update];
 * elapsed time updates automatically via the chronometer / `setWhen`.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startTimeMs = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                rows = 0L
                files = intent.getIntExtra(EXTRA_FILES, 0)
                val notif = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIF_ID, notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIF_ID, notif)
                }
            }
            ACTION_UPDATE -> {
                rows = intent.getLongExtra(EXTRA_ROWS, rows)
                files = intent.getIntExtra(EXTRA_FILES, files)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification())
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ── Notification construction ─────────────────────────────────────────────

    private var startTimeMs: Long = 0L
    private var rows: Long = 0L
    private var files: Int = 0

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun contentText(): String {
        val fileLabel = "$files file" + if (files != 1) "s" else ""
        return "$rows rows written  •  $fileLabel"
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 progress-centric notification (indeterminate — a recording
            // session has no defined endpoint).
            val style = Notification.ProgressStyle()
                .setProgressIndeterminate(true)
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Recording in progress")
                .setContentText(contentText())
                .setStyle(style)
                .setUsesChronometer(true)
                .setWhen(startTimeMs)
                .setOngoing(true)
                .setContentIntent(contentIntent())
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Recording in progress")
                .setContentText(contentText())
                .setUsesChronometer(true)
                .setWhen(startTimeMs)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent())
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Recording sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of an active sensor recording session"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "recording_session"
        private const val NOTIF_ID = 1001

        private const val ACTION_START  = "com.rfsat.shimmerenact.RECORDING_START"
        private const val ACTION_UPDATE = "com.rfsat.shimmerenact.RECORDING_UPDATE"
        private const val ACTION_STOP   = "com.rfsat.shimmerenact.RECORDING_STOP"

        private const val EXTRA_START_TIME = "start_time"
        private const val EXTRA_ROWS       = "rows"
        private const val EXTRA_FILES      = "files"

        /** Start the foreground service for a new recording session. */
        fun start(context: Context, startTimeMs: Long, fileCount: Int) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_TIME, startTimeMs)
                putExtra(EXTRA_FILES, fileCount)
            }
            context.startForegroundService(intent)
        }

        /** Push a row-count update to the ongoing notification. */
        fun update(context: Context, rows: Long, fileCount: Int) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_ROWS, rows)
                putExtra(EXTRA_FILES, fileCount)
            }
            // Service is already in the foreground; a plain startService is allowed.
            try { context.startService(intent) } catch (_: Exception) { /* app backgrounded edge case */ }
        }

        /** Stop the service and dismiss the notification. */
        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            try { context.startService(intent) } catch (_: Exception) { }
        }
    }
}
