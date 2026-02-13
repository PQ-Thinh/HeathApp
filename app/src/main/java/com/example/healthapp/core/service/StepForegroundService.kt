package com.example.healthapp.core.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.healthapp.MainActivity
import com.example.healthapp.R
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.helperEnumAndData.RunState // Đảm bảo import Enum này
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

    private var currentRunState = RunState.IDLE

    // Các biến lưu dữ liệu
    private var runStartSteps = 0
    private var runStartTime = 0L
    private var currentRawSteps = 0

    // Biến đếm thời gian thực tế (để hiển thị khi Pause)
    private var displayDurationSeconds = 0L

    companion object {
        const val CHANNEL_ID = "health_tracker_channel"
        const val NOTIFICATION_ID = 999
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDataStore()
        observeSensor()
    }

    private fun observeDataStore() {
        serviceScope.launch {
            dataStore.data.collectLatest { prefs ->
                val stateName = prefs[StepViewModel.PREF_RUN_STATE] ?: RunState.IDLE.name
                val newState = try { RunState.valueOf(stateName) } catch (_: Exception) { RunState.IDLE }

                runStartSteps = prefs[StepViewModel.PREF_START_STEPS] ?: 0
                runStartTime = prefs[StepViewModel.PREF_START_TIME] ?: 0L

                // 2. Xử lý logic khi trạng thái thay đổi
                if (currentRunState != newState) {
                    currentRunState = newState
                    when (newState) {
                        RunState.RUNNING -> {
                            if (!isServiceRunning) {
                                isServiceRunning = true
                                startForegroundCompact()

                            }
                            startTimerTicker()
                            updateNotification() // Đổi nút thành PAUSE
                        }
                        RunState.PAUSED -> {
                            //stopTimerTicker()
                            updateNotification()
                        }
                        RunState.IDLE -> {
                            isServiceRunning = false
                           // stopTimerTicker()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            stopSelf() // Tự hủy nếu IDLE
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun observeSensor() {
        serviceScope.launch {
            sensorManager.stepFlow.collectLatest { totalSteps ->
                currentRawSteps = totalSteps
                // Chỉ update Notification nếu đang CHẠY. Nếu Pause thì giữ nguyên số bước cũ.
                if (currentRunState == RunState.RUNNING) {
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    startForegroundCompact()
                }
            }
            ACTION_PAUSE -> {
                // User bấm Pause trên Notif -> Ghi PAUSED vào DataStore
                serviceScope.launch {
                    dataStore.edit { it[StepViewModel.PREF_RUN_STATE] = RunState.PAUSED.name }
                }
            }
            ACTION_RESUME -> {
                // User bấm Resume trên Notif -> Ghi RUNNING vào DataStore
                serviceScope.launch {
                    dataStore.edit { it[StepViewModel.PREF_RUN_STATE] = RunState.RUNNING.name }
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    dataStore.edit { it.remove(StepViewModel.PREF_RUN_STATE) }
                }
                stopSelf()
            }
        }
        return START_STICKY
    }
    private fun startTimerTicker() {
        serviceScope.launch {
            while (isServiceRunning) {
                // Chỉ cập nhật thời gian và notification khi trạng thái là RUNNING
                if (currentRunState == RunState.RUNNING) {
                    // Tính thời gian dựa trên SystemTime để chính xác
                    if (runStartTime > 0L) {
                        displayDurationSeconds = (System.currentTimeMillis() - runStartTime) / 1000
                    }
                    updateNotification()
                }
                delay(1000)
            }
        }
    }
    private fun updateNotification() {
        if (!isServiceRunning) return

        val notification = when (currentRunState) {
            RunState.RUNNING, RunState.PAUSED -> {
                // --- CHẾ ĐỘ RUN TRACKING (Kể cả khi Pause) ---
                val effectiveStartSteps = if (runStartSteps == 0) currentRawSteps else runStartSteps
                val sessionSteps = (currentRawSteps - effectiveStartSteps).coerceAtLeast(0)

                buildRunNotification(sessionSteps, formatDuration(displayDurationSeconds), currentRunState == RunState.PAUSED)
            }
            else -> {
                // --- CHẾ ĐỘ DAILY ---
                buildDailyNotification(currentRawSteps)
            }
        }

        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun buildRunNotification(steps: Int, timeStr: String, isPaused: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Tạo Action Pause/Resume ngay trên Notification (Tùy chọn nâng cao UX)
        val actionIntent = Intent(this, StepForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val actionPendingIntent = PendingIntent.getService(this, 1, actionIntent, PendingIntent.FLAG_IMMUTABLE)

        val actionTitle = if (isPaused) "Tiếp tục" else "Tạm dừng"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isPaused) "Đã tạm dừng: $timeStr" else "Đang chạy: $timeStr")
            .setContentText("Số bước: $steps ${if(isPaused) "(Tạm nghỉ)" else "| Cố lên! 🔥"}")
            .setSmallIcon(R.mipmap.logoapp)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true) // Không cho vuốt tắt
            .addAction(R.drawable.ic_launcher_foreground, actionTitle, actionPendingIntent) // Thêm nút bấm trên notif
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

    @SuppressLint("DefaultLocale")
    private fun formatDuration(seconds: Long): String {
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    @SuppressLint("ObsoleteSdkInt")
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