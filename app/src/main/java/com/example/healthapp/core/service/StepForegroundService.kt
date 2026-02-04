package com.example.healthapp.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
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

    // --- Biến lưu trạng thái phiên chạy (Đồng bộ với ViewModel) ---
    private var sessionStartSteps = 0
    private var sessionStartTime = 0L
    private var isSessionRunning = false

    // --- Biến CACHE giá trị bước chân mới nhất từ cảm biến ---
    private var currentRawSteps = 0

    companion object {
        const val CHANNEL_ID = "step_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Lắng nghe DataStore ngay khi Service khởi tạo
        observeDataStore()
    }

    // 1. Lắng nghe DataStore để biết khi nào Start/Stop/Pause từ UI
    private fun observeDataStore() {
        serviceScope.launch {
            dataStore.data.collectLatest { prefs ->
                isSessionRunning = prefs[StepViewModel.PREF_IS_RUNNING] ?: false
                sessionStartSteps = prefs[StepViewModel.PREF_START_STEPS] ?: 0
                sessionStartTime = prefs[StepViewModel.PREF_START_TIME] ?: 0L

                // Cập nhật ngay lập tức khi có thay đổi trạng thái
                updateNotification(currentRawSteps)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    // Start notification ngay lập tức để tránh crash
                    startForegroundCompact()
                    // Bắt đầu đếm bước
                    startStepTracking()
                }
            }
            ACTION_STOP -> {
                isServiceRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startStepTracking() {
        //Lắng nghe cảm biến và cập nhật biến Cache
        serviceScope.launch {
            sensorManager.stepFlow.collectLatest { totalSteps ->
                currentRawSteps = totalSteps
                updateNotification(totalSteps)
            }
        }

        //Timer cập nhật đồng hồ mỗi giây
        serviceScope.launch {
            while (isServiceRunning) {
                if (isSessionRunning && sessionStartTime > 0L) {
                    // Dùng biến cache thay vì gọi flow
                    updateNotification(currentRawSteps)
                }
                delay(1000)
            }
        }
    }

    private fun updateNotification(currentTotalSteps: Int) {
        if (!isServiceRunning) return

        // Tính số bước trong phiên chạy
        // Nếu chưa Start (sessionStartSteps = 0) thì hiển thị 0
        val stepsTaken = if (sessionStartSteps > 0 && currentTotalSteps >= sessionStartSteps) {
            currentTotalSteps - sessionStartSteps
        } else {
            0
        }

        // Tính thời gian
        val duration = if (isSessionRunning && sessionStartTime > 0L) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else 0L

        val timeString = formatDuration(duration)
        val statusText = if (isSessionRunning) "Đang chạy..." else "Đã tạm dừng"

        val notification = buildNotification(stepsTaken, timeString, statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(steps: Int, time: String, status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Run Tracker: $time")
            .setContentText("$status | $steps bước")
            .setSmallIcon(R.drawable.health) // Đảm bảo icon này tồn tại
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // Không rung lại khi update
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundCompact() {
        val notification = buildNotification(0, "00:00", "Đang khởi động...")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        )
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Step Tracker", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}