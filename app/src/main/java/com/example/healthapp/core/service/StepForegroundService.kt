package com.example.healthapp.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.healthapp.MainActivity
import com.example.healthapp.R
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.viewmodel.StepViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class StepForegroundService : Service() {

    @Inject lateinit var sensorManager: HealthSensorManager
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isServiceRunning = false

    // State đồng bộ từ ViewModel
    private var isRunMode = false
    private var runStartSteps = 0
    private var runStartTime = 0L

    // Cache bước hiện tại từ sensor
    private var currentRawSteps = 0

    companion object {
        const val CHANNEL_ID = "health_tracker_channel"
        const val NOTIFICATION_ID = 999
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDataStore() // 1. Lắng nghe lệnh Start/Stop
        observeSensor()    // 2. Lắng nghe bước chân
    }

    // Lắng nghe DataStore để biết khi nào chuyển chế độ
    private fun observeDataStore() {
        serviceScope.launch {
            dataStore.data.collectLatest { prefs ->
                isRunMode = prefs[StepViewModel.PREF_IS_RUNNING] ?: false
                runStartSteps = prefs[StepViewModel.PREF_START_STEPS] ?: 0
                runStartTime = prefs[StepViewModel.PREF_START_TIME] ?: 0L
                updateNotification()
                Log.d("StepDebug", "Service Sync: Mode=$isRunMode, StartSteps=$runStartSteps")
            }
        }
    }

    private fun observeSensor() {
        serviceScope.launch {
            sensorManager.stepFlow.collectLatest { totalSteps ->
                currentRawSteps = totalSteps
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            if (!isServiceRunning) {
                isServiceRunning = true
                startForegroundCompact()
                startTimerTicker()
            }
        } else if (intent?.action == ACTION_STOP) {
            isServiceRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    // Cập nhật đồng hồ mỗi giây
    private fun startTimerTicker() {
        serviceScope.launch {
            while (isServiceRunning) {
                if (isRunMode) updateNotification()
                delay(1000)
            }
        }
    }

    private fun updateNotification() {
        if (!isServiceRunning) return

        val notification = if (isRunMode) {
            // --- CHẾ ĐỘ RUN TRACKING ---

            // Nếu runStartSteps = 0 (do ViewModel chưa kịp lưu), ta tạm lấy currentRawSteps làm mốc
            // Kết quả: 1789 - 1789 = 0 (ĐÚNG)
            val effectiveStartSteps = if (runStartSteps == 0) currentRawSteps else runStartSteps

            val sessionSteps = (currentRawSteps - effectiveStartSteps).coerceAtLeast(0)
            Log.d("StepDebug", "Service Notif (RUN): Raw($currentRawSteps) - Start($effectiveStartSteps) = Show($sessionSteps)")

            val durationSeconds = if (runStartTime > 0L) {
                (System.currentTimeMillis() - runStartTime) / 1000
            } else 0L

            buildRunNotification(sessionSteps, formatDuration(durationSeconds))
        } else {
            Log.d("StepDebug", "Service Notif (DAILY): Show($currentRawSteps)")
            // --- CHẾ ĐỘ DAILY (Mặc định) ---
            buildDailyNotification(currentRawSteps)
        }

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildRunNotification(steps: Int, timeStr: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang chạy: $timeStr")
            .setContentText("Số bước: $steps | Cố lên! 🔥")
            .setSmallIcon(R.mipmap.logoapp)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildDailyNotification(totalSteps: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đếm bước hàng ngày")
            .setContentText("Tổng hôm nay: $totalSteps")
            .setSmallIcon(R.mipmap.logoapp)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompact() {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildDailyNotification(0),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        )
    }

    private fun formatDuration(seconds: Long): String {
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Health Tracker", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}