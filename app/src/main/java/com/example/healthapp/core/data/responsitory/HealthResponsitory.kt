package com.example.healthapp.core.data.responsitory

import android.util.Log
import com.example.healthapp.core.data.FirebaseSyncManager
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.SleepBucket
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.UUID
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class HealthRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val syncManager: FirebaseSyncManager,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // Đồng bộ Profile
    suspend fun syncUserToCloud(user: UserEntity) {
        firestore.collection("users").document(user.id)
            .set(user, SetOptions.merge())
            .await()
    }

    //Lấy DailyHealth (Realtime Flow)
    fun getDailyHealth(date: String, userId: String): Flow<DailyHealthEntity?> = callbackFlow {
        val docRef = firestore.collection("users").document(userId)
            .collection("daily_health").document(date)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("HealthRepository", "Lỗi Realtime DailyHealth: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.toObject(DailyHealthEntity::class.java)
                trySend(data)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

//Đồng bộ Steps & HeartRate Avg từ HC -> Firestore
    suspend fun syncHealthData(userId: String?) {
        if (userId.isNullOrEmpty()) return

        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()
        val todayStr = now.toLocalDate().toString()

        val hcSteps = healthConnectManager.readSteps(startOfDay, now)
        val hcHeartRateAvg = healthConnectManager.readHeartRate(startOfDay, now)

        val data = mapOf(
            "date" to todayStr,
            "userId" to userId,
            "steps" to hcSteps,
            "heartRateAvg" to hcHeartRateAvg,

        )

        firestore.collection("users").document(userId)
            .collection("daily_health").document(todayStr)
            .set(data, SetOptions.merge())
            .await()

        Log.d("HealthRepo", "Synced HC Steps to Cloud: $hcSteps")
    }

    suspend fun updateLocalSteps(userId: String?, steps: Int, calories: Float) {
        if (userId.isNullOrEmpty()) return
        val today = LocalDate.now().toString()

        val data = mapOf(
            "date" to today,
            "userId" to userId,
            "steps" to steps,
            "caloriesBurned" to calories
        )

        firestore.collection("users").document(userId)
            .collection("daily_health").document(today)
            .set(data, SetOptions.merge())
            .await()
    }

    //Ghi bước chân thủ công
    suspend fun writeStepsToHealthConnect(start: LocalDateTime, end: LocalDateTime, stepsDelta: Int) {
        val userId = currentUserId ?: return

        healthConnectManager.writeSteps(start, end, stepsDelta)

        val startTime = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val record = StepRecordEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startTime,
            endTime = endTime,
            count = stepsDelta
        )

        firestore.collection("users").document(userId)
            .collection("step_records").document(record.id)
            .set(record)
            .await()
    }
    suspend fun deleteStepRecord(record: StepRecordEntity) {
        try {
            // Xóa trên Health Connect
            val timeRangeFilter = TimeRangeFilter.between(
                Instant.ofEpochMilli(record.startTime),
                Instant.ofEpochMilli(record.endTime)
            )
            healthConnectManager.deleteRecords(StepsRecord::class, timeRangeFilter)

            // B. Xóa trên Firestore
            firestore.collection("users").document(record.userId)
                .collection("step_records")
                .document(record.id)
                .delete()
                .await()

            Log.d("HealthRepository", "Deleted step record: ${record.id}")
        } catch (e: Exception) {
            Log.e("HealthRepository", "Error deleting step record", e)
        }
    }

    //Lưu Nhịp tim
    suspend fun saveHeartRate(userId: String, bpm: Int) {
        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()
        val todayStr = now.toLocalDate().toString()

        val success = healthConnectManager.writeHeartRate(bpm, now)

        if (success) {
            // TÍNH TOÁN LẠI TRUNG BÌNH CỘNG TRONG NGÀY
            val avgBpm = healthConnectManager.readHeartRate(startOfDay, now)

            val dailyUpdate = mapOf(
                "date" to todayStr,
                "userId" to userId,
                "heartRateAvg" to avgBpm
            )
            firestore.collection("users").document(userId)
                .collection("daily_health").document(todayStr)
                .set(dailyUpdate, SetOptions.merge())

            // Lưu log chi tiết bản ghi
            val timeMilli = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val record = HeartRateRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                time = timeMilli,
                bpm = bpm
            )
            firestore.collection("users").document(userId)
                .collection("heart_rate_records").document(record.id)
                .set(record)
        }
    }
    suspend fun deleteHeartRate(record: HeartRateRecordEntity) {
        try {
            // Xóa trên Health Connect (Dựa vào thời gian ghi nhận)
            // Vì nhịp tim là thời điểm tức thời, ta xóa trong khoảng nhỏ 1 giây quanh thời điểm đó
            val time = Instant.ofEpochMilli(record.time)
            val timeRangeFilter = TimeRangeFilter.between(
                time.minusMillis(100),
                time.plusMillis(1000) // +1 giây
            )
           healthConnectManager.deleteRecords(HeartRateRecord::class, timeRangeFilter)

            // Xóa trên Firestore
            firestore.collection("users").document(record.userId)
                .collection("heart_rate_records") // Đảm bảo tên collection này khớp với lúc bạn lưu
                .document(record.id)
                .delete()
                .await()

            Log.d("HealthRepository", "Deleted heart rate: ${record.id}")
        } catch (e: Exception) {
            Log.e("HealthRepository", "Error deleting heart rate", e)
        }
    }

    // Lưu Giấc ngủ
    suspend fun saveSleepSession(userId: String, start: LocalDateTime, end: LocalDateTime) {
        healthConnectManager.writeSleepSession(start, end)

        val durationMinutes = Duration.between(start, end).toMinutes()
        val today = LocalDate.now().toString()

        val dailyUpdate = mapOf(
            "date" to today,
            "userId" to userId,
            "sleepHours" to durationMinutes
        )
        firestore.collection("users").document(userId)
            .collection("daily_health").document(today)
            .set(dailyUpdate, SetOptions.merge())

        val startTime = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sessionEntity = SleepSessionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startTime,
            endTime = endTime,
            type = "Sleep"
        )
        firestore.collection("users").document(userId)
            .collection("sleep_sessions").document(sessionEntity.id)
            .set(sessionEntity)
    }
    suspend fun deleteSleepSession(session: SleepSessionEntity) {
        try {
            // A. Xóa trên Health Connect (Theo khoảng thời gian ngủ)
            val timeRangeFilter = TimeRangeFilter.between(
                Instant.ofEpochMilli(session.startTime),
                Instant.ofEpochMilli(session.endTime)
            )
            healthConnectManager.deleteRecords(SleepSessionRecord::class, timeRangeFilter)

            // B. Xóa trên Firestore
            firestore.collection("users").document(session.userId)
                .collection("sleep_sessions")
                .document(session.id)
                .delete()
                .await()

            Log.d("HealthRepository", "Deleted sleep session: ${session.id}")
        } catch (e: Exception) {
            Log.e("HealthRepository", "Error deleting sleep session", e)
        }
    }

    // --- BIỂU ĐỒ & LỊCH SỬ ---

    suspend fun getStepChartData(range: ChartTimeRange): List<StepBucket> {
        val userId = currentUserId ?: return emptyList()
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = getStartDate(range)
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)

        val hcData = healthConnectManager.readStepChartData(start, end, period)
        if (hcData.any { it.totalSteps > 0 }) return hcData.sortedBy { it.startTime }

        val startDateStr = start.toLocalDate().toString()
        val endDateStr = end.toLocalDate().toString()

        val snapshot = firestore.collection("users").document(userId)
            .collection("daily_health")
            .whereGreaterThanOrEqualTo("date", startDateStr)
            .whereLessThanOrEqualTo("date", endDateStr)
            .get().await()

        val firestoreList = snapshot.toObjects(DailyHealthEntity::class.java).map { entity ->
            val date = LocalDate.parse(entity.date)
            StepBucket(
                startTime = date.atStartOfDay(),
                endTime = date.plusDays(1).atStartOfDay(),
                totalSteps = entity.steps.toLong()
            )
        }
        return firestoreList.sortedBy { it.startTime }
    }

    suspend fun getSleepChartData(range: ChartTimeRange): List<SleepBucket> {
        val userId = currentUserId ?: return emptyList()
        val end = LocalDate.now().plusDays(1).atStartOfDay()
        val start = getStartDate(range)
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)

        val hcData = healthConnectManager.readSleepChartData(start, end, period)
        if (hcData.isNotEmpty()) return hcData

        val startTimeL = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = firestore.collection("users").document(userId)
            .collection("sleep_sessions")
            .whereGreaterThanOrEqualTo("startTime", startTimeL)
            .orderBy("startTime")
            .get().await()

        val sessions = snapshot.toObjects(SleepSessionEntity::class.java)
        val zoneId = ZoneId.systemDefault()

        val grouped = sessions.groupBy {
            Instant.ofEpochMilli(it.endTime).atZone(zoneId).toLocalDate()
        }

        return grouped.map { (date, list) ->
            val totalMinutes = list.sumOf { (it.endTime - it.startTime) / 60000 }
            SleepBucket(startTime = date.atStartOfDay(), totalMinutes = totalMinutes)
        }.sortedBy { it.startTime }
    }

    suspend fun getHeartRateChartData(range: ChartTimeRange): List<HeartRateBucket> {
        val userId = currentUserId ?: return emptyList()

        // Xác định thời gian chuẩn xác
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()

        val (start, end, isDayView) = when (range) {
            ChartTimeRange.DAY -> Triple(
                now.toLocalDate().atStartOfDay(),
                now,
                true
            )
            ChartTimeRange.WEEK -> Triple(
                now.minusDays(6).toLocalDate().atStartOfDay(),
                now.plusDays(1).toLocalDate().atStartOfDay(),
                false
            )
            ChartTimeRange.MONTH -> Triple(
                now.minusDays(30).toLocalDate().atStartOfDay(),
                now.plusDays(1).toLocalDate().atStartOfDay(),
                false
            )
            ChartTimeRange.YEAR -> Triple(
                now.minusYears(1).withDayOfMonth(1),
                now.plusDays(1).toLocalDate().atStartOfDay(),
                false
            )
        }

        //Lấy từ Health Connect (Đã tối ưu dùng Aggregation)
        val hcData = if (isDayView) {
            // Nếu xem NGÀY -> Chia theo mỗi 1 GIỜ
            healthConnectManager.readHeartRateAggregationByDuration(start, end, Duration.ofHours(1))
        } else {
            // Nếu xem TUẦN/THÁNG -> Chia theo mỗi 1 NGÀY
            healthConnectManager.readHeartRateAggregationByPeriod(start, end, Period.ofDays(1))
        }

        // Nếu Health Connect có dữ liệu thì trả về luôn
        if (hcData.isNotEmpty()) return hcData

        // 3. ƯU TIÊN 2: Fallback về Firestore (Nếu không có Health Connect)
        val startTimeL = start.atZone(zoneId).toInstant().toEpochMilli()
        val endTimeL = end.atZone(zoneId).toInstant().toEpochMilli() // Thêm chặn trên để không lấy thừa tương lai

        val snapshot = firestore.collection("users").document(userId)
            .collection("heart_rate_records")
            .whereGreaterThanOrEqualTo("time", startTimeL)
            .whereLessThanOrEqualTo("time", endTimeL) // Đảm bảo chỉ lấy trong khoảng
            .orderBy("time", Query.Direction.ASCENDING)
            .get().await()

        val records = snapshot.toObjects(HeartRateRecordEntity::class.java)
        if (records.isEmpty()) return emptyList()

        // GOM NHÓM DỮ LIỆU FIRESTORE
        val grouped = if (isDayView) {
            // Xem NGÀY: Gom theo GIỜ (00:00, 01:00...)
            records.groupBy {
                Instant.ofEpochMilli(it.time).atZone(zoneId).toLocalDateTime()
                    .withMinute(0).withSecond(0).withNano(0)
            }
        } else {
            // Xem TUẦN/THÁNG: Gom theo NGÀY
            records.groupBy {
                Instant.ofEpochMilli(it.time).atZone(zoneId).toLocalDate().atStartOfDay()
            }
        }

        return grouped.map { (timeKey, list) ->
            HeartRateBucket(
                startTime = timeKey,
                min = list.minOf { it.bpm }.toLong(),
                max = list.maxOf { it.bpm }.toLong(),
                avg = list.map { it.bpm }.average().toLong()
            )
        }.sortedBy { it.startTime }
    }    // --- CÁC HÀM LỊCH SỬ (QUAN TRỌNG) ---

    suspend fun getStepRecordHistory(): List<StepRecordEntity> {
        val uid = currentUserId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("step_records")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .get().await()
            .toObjects(StepRecordEntity::class.java)
    }

    suspend fun getHeartRateHistory(): List<HeartRateRecordEntity> {
        val uid = currentUserId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("heart_rate_records")
            .orderBy("time", Query.Direction.DESCENDING)
            .get().await()
            .toObjects(HeartRateRecordEntity::class.java)
    }

    suspend fun getSleepHistory(): List<SleepSessionEntity> {
        val uid = currentUserId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("sleep_sessions")
            .orderBy("startTime", Query.Direction.DESCENDING)
            .get().await()
            .toObjects(SleepSessionEntity::class.java)
    }


    private fun getStartDate(range: ChartTimeRange): LocalDateTime {
        val today = LocalDate.now()
        return when (range) {
            ChartTimeRange.WEEK -> today.minusDays(6).atStartOfDay()
            ChartTimeRange.MONTH -> today.minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> today.minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> today.minusDays(6).atStartOfDay()
        }
    }

    fun startRealtimeSync(uid: String, onNewInvite: (InvitationEntity) -> Unit) {
        // Gọi xuống Manager và truyền tiếp callback đi
        syncManager.startListeningForInvitations(uid, onNewInvite)
    }

    // Hàm hủy lắng nghe (Gọi khi đăng xuất)
    fun stopRealtimeSync() {
        syncManager.stopListening()
    }
}

enum class ChartTimeRange { DAY, WEEK, MONTH, YEAR}