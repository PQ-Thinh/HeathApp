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
import java.time.Duration
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
    private val LAST_SYNCED_STEPS_KEY = intPreferencesKey("last_synced_steps_total")
    private val LAST_SYNC_TIME_KEY = stringPreferencesKey("last_sync_time")
    private val CURRENT_MODE_KEY = stringPreferencesKey("current_mode")

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SWITCH_MODE = "ACTION_SWITCH_MODE"
        const val EXTRA_MODE = "EXTRA_MODE"
    }
    private var currentMode = "Chạy Bộ"

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
            ACTION_SWITCH_MODE -> {
                val newMode = intent.getStringExtra(EXTRA_MODE) ?: "Chạy Bộ"
                currentMode = newMode

                //Lưu vào DataStore để UI cập nhật theo
                serviceScope.launch {
                    dataStore.edit { prefs ->
                        prefs[CURRENT_MODE_KEY] = newMode
                    }
                }

                if (newMode == "Chạy Bộ") {
                    sensorManager.resumeTracking()
                    updateNotification(isPaused = false)
                } else {
                    sensorManager.pauseTracking()
                    updateNotification(isPaused = true)
                }
            }
        }
        return START_STICKY
    }

    // Biến global để update notification nhanh
    private var currentSteps = 0
    private var currentCalories = 0

    private fun startTracking() {
        createNotificationChannel()
        startForegroundServiceCompact()

        serviceScope.launch {
            //ĐỌC DỮ LIỆU CŨ (QUAN TRỌNG: Phải làm trong Coroutine)
            val prefs = dataStore.data.first()
            val savedDate = prefs[LAST_SAVED_DATE_KEY] ?: ""
            var startOfDaySteps = prefs[START_OF_DAY_STEPS_KEY] ?: 0
            currentMode = prefs[CURRENT_MODE_KEY] ?: "Chạy Bộ"

            // Sync variables
            var lastSyncedStepsTotal = prefs[LAST_SYNCED_STEPS_KEY] ?: 0
            var lastSyncTimeStr = prefs[LAST_SYNC_TIME_KEY] ?: LocalDateTime.now().minusMinutes(16).toString()

            val today = LocalDate.now().toString()

            // Lấy User
            val email = prefs[CURRENT_USER_EMAIL_KEY]
            var userId: Int? = null
            if (email != null) {
                val user = healthDao.getUserByEmail(email)
                userId = user?.id
            }

            // Xử lý chuyển ngày: Nếu ngày lưu khác hôm nay -> Reset mọi thứ
            if (savedDate != today) {
                startOfDaySteps = 0
                lastSyncedStepsTotal = 0
                dataStore.edit {
                    it[LAST_SAVED_DATE_KEY] = today
                    it[START_OF_DAY_STEPS_KEY] = 0
                    it[LAST_SYNCED_STEPS_KEY] = 0
                }
            }

            // 2. LẮNG NGHE CẢM BIẾN
            sensorManager.stepFlow.collect { totalStepsSinceBoot ->

                // --- LOGIC 1: Xử lý Reboot máy (Cảm biến bị reset về 0) ---
                // Nếu tổng bước từ sensor nhỏ hơn mốc đầu ngày -> Chắc chắn máy đã khởi động lại
                if (totalStepsSinceBoot < startOfDaySteps) {
                    startOfDaySteps = 0
                    dataStore.edit { it[START_OF_DAY_STEPS_KEY] = 0 }
                }

                // --- LOGIC 2: Xử lý lần chạy đầu tiên trong ngày ---
                // Nếu chưa có mốc (0) VÀ sensor báo về số lớn (ví dụ 5000),
                // ta coi 5000 đó là mốc bắt đầu tính (để realSteps = 0)
                if (startOfDaySteps == 0) {
                    startOfDaySteps = totalStepsSinceBoot
                    dataStore.edit { it[START_OF_DAY_STEPS_KEY] = startOfDaySteps }
                }

                // --- LOGIC 3: Tính toán ---
                // Số bước thực = Tổng sensor - Mốc đầu ngày
                // Update app: startOfDaySteps được load lại (VD: 2000), Sensor vẫn chạy (VD: 2100) -> 100 bước. KHÔNG MẤT.
                val realSteps = (totalStepsSinceBoot - startOfDaySteps).coerceAtLeast(0)
                currentSteps = realSteps
                currentCalories = (realSteps * 0.04).toInt()

                if (!sensorManager.isPaused) {
                    updateNotification(isPaused = false)

                    // A. Update DB Local (liên tục để UI mượt)
                    if (userId != null) {
                        repository.updateLocalSteps(userId, currentSteps, currentCalories.toFloat())
                    }

                    // B. Sync Health Connect (15 phút/lần)
                    val now = LocalDateTime.now()
                    val lastSyncTime = try {
                        LocalDateTime.parse(lastSyncTimeStr)
                    } catch (e: Exception) { now.minusMinutes(16) }

                    val stepsToAdd = currentSteps - lastSyncedStepsTotal
                    val timeDiff = Duration.between(lastSyncTime, now).toMinutes()

                    if (stepsToAdd > 0 && timeDiff >= 15) {
                        repository.writeStepsToHealthConnect(lastSyncTime, now, stepsToAdd)

                        lastSyncedStepsTotal = currentSteps
                        lastSyncTimeStr = now.toString()

                        dataStore.edit {
                            it[LAST_SYNCED_STEPS_KEY] = lastSyncedStepsTotal
                            it[LAST_SYNC_TIME_KEY] = lastSyncTimeStr
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

        // 1. XÁC ĐỊNH ACTION CHO NÚT BẤM
        val actionIntent = Intent(this, StepForegroundService::class.java)
        val actionTitle: String
        val actionIcon: Int

        if (currentMode == "Chạy Bộ") {
            // Đang chạy thì Pause/Resume
            if (isPaused) {
                actionIntent.action = ACTION_RESUME
                actionTitle = "Tiếp tục"
                actionIcon = R.drawable.ic_launcher_foreground // Icon Play
            } else {
                actionIntent.action = ACTION_PAUSE
                actionTitle = "Tạm dừng"
                actionIcon = R.drawable.ic_launcher_foreground // Icon Pause
            }
        } else {
            // Đang Đạp xe/Leo núi -> Nút bấm để quay lại đi bộ
            // Gửi action chuyển mode về "Chạy Bộ"
            actionIntent.action = ACTION_SWITCH_MODE
            actionIntent.putExtra(EXTRA_MODE, "Chạy Bộ")

            actionTitle = "Chuyển sang Đi bộ"
            actionIcon = R.drawable.ic_launcher_foreground // Icon Đi bộ
        }

        val actionPendingIntent = PendingIntent.getService(
            this,
            1,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. NỘI DUNG HIỂN THỊ
        val contentText = if (isPaused) {
            if (currentMode == "Chạy Bộ") "Đang tạm dừng - $currentSteps bước"
            else "Đang theo dõi hoạt động khác" // Đạp xe, Leo núi...
        } else {
            "$currentSteps bước • $currentCalories kcal"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentMode) // Tiêu đề là tên môn thể thao
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            // Thêm nút bấm đã được cấu hình ở trên
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