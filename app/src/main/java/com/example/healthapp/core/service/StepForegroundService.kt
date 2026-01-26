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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
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

    // Key mới: Để tránh gửi trùng hoặc reset bước chân trên Health Connect
    private val LAST_SYNCED_STEPS_KEY = intPreferencesKey("last_synced_steps_total")
    private val LAST_SYNC_TIME_KEY = stringPreferencesKey("last_sync_time")

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
        startForegroundServiceCompact()

        serviceScope.launch {
            // 1. Khôi phục dữ liệu từ DataStore
            val prefs = dataStore.data.first()
            val savedDate = prefs[LAST_SAVED_DATE_KEY] ?: ""
            var startOfDaySteps = prefs[START_OF_DAY_STEPS_KEY] ?: 0

            // Khôi phục mốc đã sync lên Health Connect để tính delta (chênh lệch)
            var lastSyncedStepsTotal = prefs[LAST_SYNCED_STEPS_KEY] ?: 0
            var lastSyncTimeStr = prefs[LAST_SYNC_TIME_KEY] ?: LocalDateTime.now().toString()

            val today = LocalDate.now().toString()

            val email = prefs[CURRENT_USER_EMAIL_KEY]
            var userId: Int? = null
            if (email != null) {
                val user = healthDao.getUserByEmail(email)
                userId = user?.id
            } else {
                // Nếu chưa đăng nhập (không có email), thử lấy User đầu tiên trong DB làm fallback
                // Hoặc bỏ qua luôn nếu muốn chặt chẽ
//                val firstUser = healthDao.getUserFlowByEmail().firstOrNull()?.get(0)
//                userId = firstUser?.id
            }
            // Reset nếu sang ngày mới
            if (savedDate != today) {
                startOfDaySteps = 0
                lastSyncedStepsTotal = 0 // Ngày mới thì chưa sync gì cả
                dataStore.edit {
                    it[LAST_SAVED_DATE_KEY] = today
                    it[START_OF_DAY_STEPS_KEY] = 0
                    it[LAST_SYNCED_STEPS_KEY] = 0
                }
            }

            // 2. LẮNG NGHE CẢM BIẾN
            sensorManager.stepFlow.collect { totalStepsSinceBoot ->

                if (startOfDaySteps == 0) {
                    startOfDaySteps = totalStepsSinceBoot
                    dataStore.edit { it[START_OF_DAY_STEPS_KEY] = startOfDaySteps }
                }

                // Tính tổng bước hôm nay
                val realSteps = (totalStepsSinceBoot - startOfDaySteps).coerceAtLeast(0)
                currentSteps = realSteps
                currentCalories = (realSteps * 0.04).toInt()

                if (!sensorManager.isPaused) {
                    updateNotification(isPaused = false)

                    // A. Cập nhật Database (Để App UI hiển thị)
                    if (userId != null) {
                        repository.updateLocalSteps(userId, currentSteps, currentCalories.toFloat())
                    }

                    // B. Đồng bộ Health Connect (Incremental Sync)
                    // Chỉ gửi khi chênh lệch > 50 bước so với lần gửi cuối
                    if (currentSteps - lastSyncedStepsTotal >= 50) {
                        val now = LocalDateTime.now()
                        val lastSyncTime = try {
                            LocalDateTime.parse(lastSyncTimeStr)
                        } catch (e: Exception) { now.minusMinutes(1) }

                        // Tính lượng bước mới đi thêm: Ví dụ 150 - 100 = 50 bước
                        val stepsToAdd = currentSteps - lastSyncedStepsTotal

                        if (stepsToAdd > 0) {
                            // Gửi gói 50 bước này lên (Health Connect sẽ cộng vào tổng)
                            repository.writeStepsToHealthConnect(lastSyncTime, now, stepsToAdd)

                            // Cập nhật mốc đã gửi
                            lastSyncedStepsTotal = currentSteps
                            lastSyncTimeStr = now.toString()

                            // Lưu mốc vào DataStore để lỡ tắt máy không bị quên
                            dataStore.edit {
                                it[LAST_SYNCED_STEPS_KEY] = lastSyncedStepsTotal
                                it[LAST_SYNC_TIME_KEY] = lastSyncTimeStr
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(isPaused: Boolean) {
        val notification = buildNotification(isPaused)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val actionIntent = Intent(this, StepForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val actionPendingIntent = PendingIntent.getService(this, 1, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val actionIcon = R.drawable.ic_launcher_foreground
        val actionTitle = if (isPaused) "Tiếp tục" else "Tạm dừng"
        val contentText = if (isPaused) "Đang tạm dừng - $currentSteps bước" else "$currentSteps bước • $currentCalories kcal"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đếm bước chân")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
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
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}