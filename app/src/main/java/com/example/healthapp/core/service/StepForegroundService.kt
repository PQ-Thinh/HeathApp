package com.example.healthapp.core.service

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

    // State riêng của Session (Lấy từ DataStore)
    private var isSessionRunning = false
    private var sessionStartSteps = 0
    private var sessionStartTime = 0L

    // Cache bước chân hiện tại
    private var currentRawSteps = 0

    companion object {
        const val CHANNEL_ID = "run_tracking_channel"
        const val NOTIFICATION_ID = 999
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDataStore()
    }

    // --- LẮNG NGHE DATASTORE ĐỂ ĐỒNG BỘ VỚI UI ---
    private fun observeDataStore() {
        serviceScope.launch {
            dataStore.data.collectLatest { prefs ->
                // Đọc đúng các Key mà ViewModel ghi
                isSessionRunning = prefs[StepViewModel.PREF_IS_RUNNING] ?: false
                sessionStartSteps = prefs[StepViewModel.PREF_START_STEPS] ?: 0
                sessionStartTime = prefs[StepViewModel.PREF_START_TIME] ?: 0L

                // Cập nhật notification ngay khi có thay đổi từ ViewModel (Start/Pause)
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    startForegroundCompact()
                    startTracking()
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

    private fun startTracking() {
        //Lắng nghe cảm biến
        serviceScope.launch {
            sensorManager.stepFlow.collectLatest { totalSteps ->
                currentRawSteps = totalSteps
                updateNotification()
            }
        }

        // Tự động update thời gian mỗi giây (Ticker cho Notification)
        serviceScope.launch {
            while (isServiceRunning) {
                if (isSessionRunning) {
                    updateNotification()
                }
                delay(1000)
            }
        }
    }

    private fun updateNotification() {
        if (!isServiceRunning) return

        // Tính toán logic y hệt ViewModel
        //Tính bước: Nếu chưa start (sessionStartSteps = 0) thì là 0.
        // Nếu đã start: Lấy Hiện tại - Mốc Start
        val stepsTaken = if (sessionStartSteps > 0 && currentRawSteps >= sessionStartSteps) {
            currentRawSteps - sessionStartSteps
        } else {
            0
        }

        // 2. Tính thời gian
        val duration = if (isSessionRunning && sessionStartTime > 0L) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else {
            0L // Hoặc giữ giá trị cuối cùng nếu muốn (logic phức tạp hơn chút)
        }

        val formattedTime = formatDuration(duration)
        val statusText = if (isSessionRunning) "Đang chạy..." else "Đã tạm dừng"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Run Tracker: $formattedTime")
            .setContentText("$statusText | $stepsTaken bước")
            .setSmallIcon(R.drawable.health) // Đảm bảo có icon này
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompact() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Health App")
            .setContentText("Đang khởi động theo dõi...")
            .setSmallIcon(R.drawable.health)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        )
    }

    private fun formatDuration(seconds: Long): String {
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Run Tracking",
                NotificationManager.IMPORTANCE_LOW // Low để không kêu ting ting mỗi giây
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}