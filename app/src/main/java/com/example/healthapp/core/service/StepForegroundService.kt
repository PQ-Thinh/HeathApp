package com.example.healthapp.core.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.healthapp.MainActivity
import com.example.healthapp.R
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class StepForegroundService : Service() {

    @Inject lateinit var sensorManager: HealthSensorManager
    @Inject lateinit var repository: HealthRepository
    @Inject lateinit var healthDao: HealthDao
    @Inject lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "step_tracking_channel"
    private val NOTIFICATION_ID = 1

    // Key DataStore
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")
    private val START_OF_DAY_STEPS_KEY = intPreferencesKey("start_of_day_steps")
    private val LAST_SAVED_DATE_KEY = stringPreferencesKey("last_saved_date")

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> {
                sensorManager.pauseTracking()
                updateNotification(isPaused = true)
            }
            ACTION_RESUME -> {
                sensorManager.resumeTracking()
                updateNotification(isPaused = false)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private var currentSteps = 0
    private var currentCalories = 0

    private fun startTracking() {
        createNotificationChannel()

        // Hiển thị thông báo ngay lập tức để Service sống
        startForegroundServiceCompact()

        serviceScope.launch {
            // 1. Lấy thông tin cơ bản
            val prefs = dataStore.data.first()
            val savedDate = prefs[LAST_SAVED_DATE_KEY] ?: ""
            var startOfDaySteps = prefs[START_OF_DAY_STEPS_KEY] ?: 0
            val today = LocalDate.now().toString()

            // Lấy User ID để lưu vào Room (chỉ cần lấy 1 lần)
            val email = prefs[CURRENT_USER_EMAIL_KEY]
            val userId = if (email != null) healthDao.getUserByEmail(email)?.id else null

            // Reset mốc nếu sang ngày mới
            if (savedDate != today) {
                startOfDaySteps = 0
                dataStore.edit {
                    it[LAST_SAVED_DATE_KEY] = today
                    it[START_OF_DAY_STEPS_KEY] = 0
                }
            }

            // 2. LẮNG NGHE CẢM BIẾN (Logic siêu đơn giản)
            sensorManager.stepFlow.collect { totalStepsSinceBoot ->

                // Set mốc lần đầu
                if (startOfDaySteps == 0) {
                    startOfDaySteps = totalStepsSinceBoot
                    dataStore.edit { it[START_OF_DAY_STEPS_KEY] = startOfDaySteps }
                }

                // Tính toán cực nhanh
                val realSteps = (totalStepsSinceBoot - startOfDaySteps).coerceAtLeast(0)
                currentSteps = realSteps
                currentCalories = (realSteps * 0.04).toInt()

                // Cập nhật UI Notification nếu không Pause
                if (!sensorManager.isPaused) {
                    updateNotification(isPaused = false)

                    // Lưu vào Room (Local DB) để giữ dữ liệu
                    // Không cần sync Health Connect ở đây cho đỡ tốn pin
                    if (userId != null) {
                        repository.updateLocalSteps(userId, currentSteps, currentCalories.toFloat())
                    }
                }
            }
        }
    }

    // Hàm update Notification: Chỉ lấy biến global currentSteps ra hiển thị, cực nhẹ
    private fun updateNotification(isPaused: Boolean) {
        val notification = buildNotification(isPaused)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        // Tạo Action cho nút bấm (Pause / Resume)
        val actionIntent = Intent(this, StepForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val actionPendingIntent = PendingIntent.getService(this, 1, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Icon và Chữ cho nút bấm
        val actionIcon = if (isPaused) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_foreground // Bạn thay icon Play/Pause ở đây
        val actionTitle = if (isPaused) "Tiếp tục" else "Tạm dừng"

        // Nội dung hiển thị
        val contentText = if (isPaused) "Đang tạm dừng - $currentSteps bước" else "$currentSteps bước • $currentCalories kcal"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đếm bước chân")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Icon nhỏ trên thanh status
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // Quan trọng: Không rung điện thoại mỗi khi cập nhật bước
            .setOngoing(true) // Không cho vuốt xóa

            // THÊM NÚT BẤM VÀO THÔNG BÁO
            .addAction(actionIcon, actionTitle, actionPendingIntent)

            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundServiceCompact() {
        val notification = buildNotification(isPaused = false)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Theo dõi bước chân",
                NotificationManager.IMPORTANCE_LOW // Low để không hiện popup che màn hình
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}