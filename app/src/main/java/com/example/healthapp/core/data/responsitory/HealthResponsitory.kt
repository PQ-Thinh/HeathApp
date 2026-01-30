package com.example.healthapp.core.data.responsitory

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.healthapp.core.data.FirebaseSyncManager
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.SleepBucket
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDateTime
import java.time.Period
import java.util.UUID
import javax.inject.Inject

class HealthRepository @Inject constructor(
    private val healthDao: HealthDao,
    private val healthConnectManager: HealthConnectManager,
    private val dataStore: DataStore<Preferences>,
    private val syncManager: FirebaseSyncManager
) {
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")

    // Lấy dữ liệu để hiển thị lên UI (Luôn lấy từ Local Cache cho nhanh)
    fun getDailyHealth(date: String, userId: String): Flow<DailyHealthEntity?> {
        return healthDao.getDailyHealth(date, userId)
    }

    //Đồng bộ từ Health Connect về Room
    suspend fun syncHealthData(userId: String?) {
        if (userId.isNullOrEmpty()) return

        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()
        val todayStr = now.toLocalDate().toString()

        // Lấy dữ liệu từ Health Connect
        val hcSteps = healthConnectManager.readSteps(startOfDay, now)

        // đồng bộ cả Nhịp tim từ health
        // val hcHeartRate = healthConnectManager.readHeartRate(startOfDay, now)

        // Lấy dữ liệu Local hiện tại
        val currentLocalData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()
        val currentLocalSteps = currentLocalData?.steps ?: 0


        // Chỉ cập nhật nếu Health Connect có nhiều bước hơn (hoặc cập nhật trường khác nếu cần)
        if (hcSteps > currentLocalSteps) {

            // dùng copy() từ dữ liệu cũ để giữ lại heartRate/sleep
            val entityToSave = if (currentLocalData == null) {
                // Nếu chưa có dòng nào -> Tạo mới
                DailyHealthEntity(
                    date = todayStr,
                    userId = userId,
                    steps = hcSteps
                )
            } else {
                // Nếu đã có -> Copy và chỉ sửa steps
                currentLocalData.copy(steps = hcSteps)
            }

            healthDao.insertOrUpdateDailyHealth(entityToSave)
            Log.d("HealthRepo", "Synced HC Steps: $hcSteps (Preserved Heart/Sleep)")

            // Đẩy lên Cloud
            syncManager.pushDailyHealth(entityToSave)
        }
    }

    suspend fun syncUserToCloud(user: UserEntity) {
        syncManager.pushUserProfile(user)
    }

    suspend fun updateLocalSteps(userId: String?, steps: Int, calories: Float) {
        if (userId.isNullOrEmpty()) return

        val today = LocalDate.now().toString()
        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()

        if (currentData == null) {
            val newData = DailyHealthEntity(
                date = today,
                userId = userId,
                steps = steps,
                caloriesBurned = calories
            )
            healthDao.insertOrUpdateDailyHealth(newData)
            syncManager.pushDailyHealth(newData)
        } else {
            // Cách 1: Dùng Update Query (nhanh gọn)
            healthDao.updateSteps(today, userId, steps, calories)

            // Cách 2: Copy object để push lên Cloud đầy đủ
            val updatedData = currentData.copy(steps = steps, caloriesBurned = calories)
            syncManager.pushDailyHealth(updatedData)
        }
    }

    suspend fun writeStepsToHealthConnect(start: LocalDateTime, end: LocalDateTime, stepsDelta: Int) {
        val prefs = dataStore.data.first()
        val email = prefs[CURRENT_USER_EMAIL_KEY] ?: return
        val user = healthDao.getUserByEmail(email) ?: return
        val userId = user.id

        //  Ghi vào Health Connect (Giữ nguyên)
        healthConnectManager.writeSteps(start, end, stepsDelta)

        //Lưu bản ghi chi tiết vào Room & Push Cloud
        val startTime = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val record = StepRecordEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startTime,
            endTime = endTime,
            count = stepsDelta
        )

        // Lưu vào Room
        healthDao.insertStepRecords(listOf(record))

        // Push lên Firebase
        syncManager.pushStepRecords(listOf(record))
    }

    suspend fun saveHeartRate(userId: String, bpm: Int) {
        val now = LocalDateTime.now()
        val todayStr = now.toLocalDate().toString()

        // Ghi vào Health Connect
        val success = healthConnectManager.writeHeartRate(bpm, now)

        if (success) {
            // Lưu vào Room
            // Phải kiểm tra xem bản ghi đã tồn tại chưa.
            // Nếu chưa có (VD: mới cài app, chưa đi bước nào) thì phải Insert mới.
            val currentData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()
            if (currentData == null) {
                val newData = DailyHealthEntity(date = todayStr, userId = userId, heartRateAvg = bpm)
                healthDao.insertOrUpdateDailyHealth(newData)
                syncManager.pushDailyHealth(newData) // Push Daily
            } else {
                healthDao.updateHeartRate(todayStr, userId, bpm)
                // Cần push lại Daily để update Avg Heart Rate lên Cloud
                val updatedData = currentData.copy(heartRateAvg = bpm)
                syncManager.pushDailyHealth(updatedData)
            }

            // Lưu bản ghi chi tiết (Record) để vẽ biểu đồ
            val timeMilli = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val record = HeartRateRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                time = timeMilli,
                bpm = bpm
            )

            healthDao.insertHeartRateRecords(listOf(record))
            syncManager.pushHeartRateRecords(listOf(record))
        }
    }

    suspend fun saveSleepSession(userId: String, start: LocalDateTime, end: LocalDateTime) {
        // Ghi HC
        healthConnectManager.writeSleepSession(start, end)

        //Tính toán & Update Daily
        val durationMinutes = Duration.between(start, end).toMinutes()
        val today = LocalDate.now().toString()
        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()
        val finalData = if (currentData == null) {
            DailyHealthEntity(date = today, userId = userId, sleepHours = durationMinutes)
        } else {
            currentData.copy(sleepHours = durationMinutes)
        }
        healthDao.insertOrUpdateDailyHealth(finalData)
        syncManager.pushDailyHealth(finalData)

        //  Lưu Session chi tiết & Push Cloud
        val startTime = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sessionEntity = SleepSessionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startTime,
            endTime = endTime,
            type = "Sleep"
        )

        healthDao.insertSleepSessions(listOf(sessionEntity))
        syncManager.pushSleepSession(sessionEntity)
    }

    suspend fun getSleepChartData(range: ChartTimeRange): List<SleepBucket> {
        val prefs = dataStore.data.first()
        val email = prefs[CURRENT_USER_EMAIL_KEY] ?: return emptyList()
        val user = healthDao.getUserByEmail(email) ?: return emptyList()
        val userId = user.id

        val today = LocalDate.now()
        val end = today.plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.WEEK -> today.minusDays(6).atStartOfDay()
            ChartTimeRange.MONTH -> today.minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> today.minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> today.minusDays(6).atStartOfDay()
        }
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)

        //[ƯU TIÊN] HC
        val hcData = healthConnectManager.readSleepChartData(start, end, period)
        if (hcData.isNotEmpty()) return hcData

        // Nếu HC trống -> Lấy từ Room
        Log.d("Chart", "HC Sleep trống, lấy từ Room...")
        val zoneId = java.time.ZoneId.systemDefault()
        val startTimeL = start.atZone(zoneId).toInstant().toEpochMilli()
        val endTimeL = end.atZone(zoneId).toInstant().toEpochMilli()

        // Hàm getSleepSessionsInRange bạn đã thêm vào DAO
        val sessions = healthDao.getSleepSessionsInRange(userId, startTimeL, endTimeL)

        // Tính tổng thời gian ngủ mỗi ngày
        val grouped = sessions.groupBy {
            java.time.Instant.ofEpochMilli(it.startTime).atZone(zoneId).toLocalDate()
        }

        return grouped.map { (date, list) ->
            // Tính tổng phút: (End - Start) / 60000
            val totalMinutes = list.sumOf { (it.endTime - it.startTime) / 60000 }
            SleepBucket(
                startTime = date.atStartOfDay(),
                totalMinutes = totalMinutes
            )
        }.sortedBy { it.startTime }
    }
    suspend fun getHeartRateChartData(range: ChartTimeRange): List<HeartRateBucket> {
        // Lấy User ID
        val prefs = dataStore.data.first()
        val email = prefs[CURRENT_USER_EMAIL_KEY] ?: return emptyList()
        val user = healthDao.getUserByEmail(email) ?: return emptyList()
        val userId = user.id

        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.DAY -> LocalDate.now().atStartOfDay()
            ChartTimeRange.WEEK -> now.minusDays(6)
            ChartTimeRange.MONTH -> now.minusDays(30)
            ChartTimeRange.YEAR -> now.minusYears(1)
        }

        // [ƯU TIÊN] HC
        val hcData = if (range == ChartTimeRange.DAY) {
            healthConnectManager.readHeartRateAggregationByDuration(start, end, Duration.ofHours(1))
        } else {
            healthConnectManager.readHeartRateAggregation(start, end, Period.ofDays(1))
        }

        if (hcData.isNotEmpty()) return hcData

        // Nếu HC trống -> Lấy từ Room
        Log.d("Chart", "HC HeartRate trống, lấy từ Room...")

        // Convert sang Mili giây để query bảng heart_rate_records
        // Lưu ý ZoneOffset: Ở VN thường là +7, nhưng tốt nhất dùng systemDefault
        val zoneId = java.time.ZoneId.systemDefault()
        val startTimeL = start.atZone(zoneId).toInstant().toEpochMilli()
        val endTimeL = end.atZone(zoneId).toInstant().toEpochMilli()

        val records = healthDao.getHeartRateRecordsList(userId, startTimeL, endTimeL)

        if (records.isEmpty()) return emptyList()

        // Gom nhóm dữ liệu thô thành Bucket (Min/Max/Avg)
        val grouped = records.groupBy {
            java.time.Instant.ofEpochMilli(it.time).atZone(zoneId).toLocalDate()
        }

        return grouped.map { (date, list) ->
            HeartRateBucket(
                startTime = date.atStartOfDay(),
                min = list.minOf { it.bpm }.toLong(),
                max = list.maxOf { it.bpm }.toLong(),
                avg = list.map { it.bpm }.average().toLong()
            )
        }.sortedBy { it.startTime }
    }
    suspend fun getStepChartData(range: ChartTimeRange): List<StepBucket> {
        //Lấy User ID (Cần để query Room)
        // Cách lấy nhanh nhất từ DataStore (để code gọn hơn đoạn flow của bạn)
        val prefs = dataStore.data.first()
        val email = prefs[CURRENT_USER_EMAIL_KEY] ?: return emptyList()
        val user = healthDao.getUserByEmail(email) ?: return emptyList()
        val userId = user.id

        // 2. Setup thời gian
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.WEEK -> LocalDate.now().minusDays(6).atStartOfDay()
            ChartTimeRange.MONTH -> LocalDate.now().minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> LocalDate.now().minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> LocalDate.now().minusDays(6).atStartOfDay()
        }
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)

        //Thử lấy từ Health Connect
        val hcData = healthConnectManager.readStepChartData(start, end, period)

        // Kiểm tra xem HC có dữ liệu thực không (hay chỉ toàn 0)
        val hasRealData = hcData.any { it.totalSteps > 0 }

        if (hasRealData) {
            //Merge thêm bước chân realtime hôm nay vào HC
            val mutableHcData = hcData.toMutableList()
            if (range != ChartTimeRange.YEAR) {
                val todayStr = LocalDate.now().toString()
                val localTodayData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()
                val realTimeSteps = localTodayData?.steps ?: 0

                // Tìm bucket hôm nay để update
                val todayIndex = mutableHcData.indexOfFirst { it.startTime.toLocalDate().isEqual(LocalDate.now()) }
                if (todayIndex != -1) {
                    if (realTimeSteps > mutableHcData[todayIndex].totalSteps) {
                        mutableHcData[todayIndex] = mutableHcData[todayIndex].copy(totalSteps = realTimeSteps.toLong())
                    }
                } else if (realTimeSteps > 0) {
                    mutableHcData.add(StepBucket(LocalDate.now().atStartOfDay(), now, realTimeSteps.toLong()))
                }
            }
            return mutableHcData.sortedBy { it.startTime }
        }

        // Nếu HC trống -> Lấy từ Room (Dữ liệu lịch sử từ Firebase)
        Log.d("Chart", "Health Connect trống, lấy Step từ Room...")

        // Cần import java.time.format.DateTimeFormatter
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val startDateStr = start.toLocalDate().format(formatter)
        val endDateStr = end.toLocalDate().format(formatter)


        val roomData = healthDao.getDailyHealthInRange(userId, startDateStr, endDateStr)

        val fallbackList = roomData.map { entity ->
            val date = LocalDate.parse(entity.date, formatter)
            StepBucket(
                startTime = date.atStartOfDay(),
                endTime = date.plusDays(1).atStartOfDay(),
                totalSteps = entity.steps.toLong()
            )
        }
        return fallbackList.sortedBy { it.startTime }
    }
    suspend fun initialSync(uid: String) {
        syncManager.pullUserData(uid)
    }
    fun startRealtimeSync(uid: String) {
        syncManager.startListeningUser(uid)
        syncManager.startListeningForInvitations(uid)
    }

    fun stopRealtimeSync() {
        syncManager.stopAllListeners()
    }
    fun getStepHistory() = healthDao.getAllStepsHistory()
    fun getHeartRateHistory() = healthDao.getAllHeartRateHistory()
    fun getSleepHistory() = healthDao.getAllSleepHistory()
}

enum class ChartTimeRange { DAY, WEEK, MONTH, YEAR}