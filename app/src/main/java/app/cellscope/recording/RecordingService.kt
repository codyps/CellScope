package app.cellscope.recording

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
import app.cellscope.CellScopeApplication
import app.cellscope.MainActivity
import app.cellscope.R
import app.cellscope.battery.BatteryReader
import app.cellscope.data.BatteryReading
import app.cellscope.data.GapReason
import app.cellscope.data.TimelineGap
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var reader: BatteryReader
    private lateinit var preferences: RecordingPreferences
    private val dao by lazy { (application as CellScopeApplication).database.batteryDao() }
    private var recordingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        reader = BatteryReader(this)
        preferences = RecordingPreferences(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISABLE) {
            preferences.recordingEnabled = false
            disableRecording()
            return START_NOT_STICKY
        }
        if (!preferences.recordingEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startRecording()
        return START_STICKY
    }

    private fun startRecording() {
        if (recordingJob != null) return
        promote(notification(null))
        recordingJob = scope.launch {
            val initialInterval = preferences.sampleIntervalMs
            val now = System.currentTimeMillis()
            val disabledGap = dao.openGap(GapReason.RECORDING_DISABLED)
            if (disabledGap != null) dao.finishGap(disabledGap.id, now)
            if (disabledGap == null) recordInferredGap(now, initialInterval)

            while (isActive && preferences.recordingEnabled) {
                val started = android.os.SystemClock.elapsedRealtime()
                val reading = reader.read()
                dao.insertSample(reading.asSample())
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(reading))
                val spent = android.os.SystemClock.elapsedRealtime() - started
                val interval = preferences.sampleIntervalMs
                delay((interval - spent).coerceAtLeast(100))
            }
        }
    }

    private suspend fun recordInferredGap(now: Long, interval: Long) {
        val latest = dao.latestSample() ?: return
        val expectedNext = latest.wallTimeMs + interval
        val threshold = maxOf(interval * 3, 30_000)
        if (now - latest.wallTimeMs > threshold) {
            dao.insertGap(
                TimelineGap(
                    startedAtMs = expectedNext,
                    endedAtMs = now,
                    reason = GapReason.RECORDER_INTERRUPTED,
                    details = "The phone was off, CellScope was stopped, or Android could not run the recorder.",
                ),
            )
        }
    }

    private fun disableRecording() {
        recordingJob?.cancel()
        recordingJob = null
        scope.launch {
            if (dao.openGap(GapReason.RECORDING_DISABLED) == null) {
                dao.insertGap(
                    TimelineGap(
                        startedAtMs = System.currentTimeMillis(),
                        reason = GapReason.RECORDING_DISABLED,
                        details = "Continuous collection was disabled in CellScope.",
                    ),
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun promote(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(reading: BatteryReading?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disableIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val detail = when {
            reading == null -> "Starting continuous battery monitoring…"
            reading.currentNowUa != null -> String.format(
                Locale.getDefault(),
                "%.0f%% · %+.0f mA · %.3f V",
                reading.levelPercent ?: 0f,
                reading.currentNowUa / 1_000f,
                (reading.voltageMv ?: 0) / 1_000f,
            )
            else -> String.format(
                Locale.getDefault(),
                "%.0f%% · %.3f V",
                reading.levelPercent ?: 0f,
                (reading.voltageMv ?: 0) / 1_000f,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("CellScope is monitoring")
            .setContentText(detail)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Disable", disableIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.recording_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        recordingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "app.cellscope.action.START_RECORDING"
        const val ACTION_DISABLE = "app.cellscope.action.DISABLE_RECORDING"
        private const val CHANNEL_ID = "battery_recording"
        private const val NOTIFICATION_ID = 7401

        fun ensureRunning(context: Context) {
            if (!RecordingPreferences(context).recordingEnabled) return
            context.startForegroundService(
                Intent(context, RecordingService::class.java).setAction(ACTION_START),
            )
        }

        fun disable(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_DISABLE),
            )
        }
    }
}
