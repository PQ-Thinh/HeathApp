package com.example.healthapp.core.data.responsitory

import android.content.Context
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
import com.example.healthapp.core.helperEnum.ChartTimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class HealthRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val syncManager: FirebaseSyncManager,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {

    private val currentUserId: String?
        get() = auth.currentUser?.uid

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

        // Lấy tổng bước "chuẩn" từ Health Connect
        // Số này chắc chắn đúng và khớp với HC
        val hcSteps = healthConnectManager.readSteps(startOfDay, now)
        val hcHeartRateAvg = healthConnectManager.readHeartRate(startOfDay, now)
        val hcSleep = healthConnectManager.readSleep(startOfDay, now)

        var currentTarget = 10000
        // Kiểm tra xem hôm nay đã có bản ghi chưa và đã có target chưa
        val todayDocRef = firestore.collection("users").document(userId)
            .collection("daily_health").document(todayStr)

        val todaySnapshot = todayDocRef.get().await()

        if (todaySnapshot.exists() && (todaySnapshot.getLong("targetSteps") ?: 0) > 0) {
            // Nếu hôm nay đã có target rồi thì giữ nguyên (để không bị reset về 10000 nếu user đã chỉnh)
            currentTarget = todaySnapshot.getLong("targetSteps")!!.toInt()
        } else {
            // Nếu hôm nay CHƯA có (ngày mới), đi lấy từ quá khứ
            currentTarget = getLastTargetSteps(userId)
        }

        syncAndCleanStepRecords(userId, startOfDay, now)
        recalculateDailySteps(userId, todayStr, startOfDay, now) // Hàm tính lại tổng bước

        //  Sync Heart Rate
        syncAndCleanHeartRecords(userId, startOfDay, now)
        recalculateDailyHeartRate(userId, todayStr, startOfDay, now) // Hàm tính lại trung bình nhịp tim

        //  Sync Sleep
        // Lấy lùi lại 1 ngày để bao trọn giấc ngủ bắt đầu từ đêm hôm trước
        syncAndCleanSleepSessions(userId, startOfDay.minusDays(1), now)
        recalculateDailySleep(userId, todayStr, startOfDay, now) // Hàm tính lại tổng giờ ngủ

        val data = mapOf(
            "date" to todayStr,
            "userId" to userId,
            "steps" to hcSteps, // Ghi đè số chuẩn vào Firebase
            "heartRateAvg" to hcHeartRateAvg,
            "sleep" to hcSleep
        )

        firestore.collection("users").document(userId)
            .collection("daily_health").document(todayStr)
            .set(data, SetOptions.merge())
            .await()

        Log.d("HealthRepo", "Đã sync tổng bước chuẩn: $hcSteps")

        //Gọi hàm đồng bộ chi tiết (Thêm Mới + Xóa Cũ)
        syncAndCleanStepRecords(userId, startOfDay, now)
    }
    //--- Lấy Target Steps từ ngày gần nhất ---
    private suspend fun getLastTargetSteps(userId: String): Int {
        return try {
            // Lấy 1 bản ghi DailyHealth gần nhất của user này
            val snapshot = firestore.collection("users").document(userId)
                .collection("daily_health")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(1)
                .get().await()

            if (!snapshot.isEmpty) {
                // Lấy target của ngày hôm đó
                val lastTarget = snapshot.documents[0].getLong("targetSteps")?.toInt() ?: 0
                if (lastTarget > 0) lastTarget else 10000
            } else {
                10000 // Nếu chưa có lịch sử nào (User mới) -> Mặc định 10000
            }
        } catch (e: Exception) {
            Log.e("HealthRepo", "Lỗi lấy target cũ: ${e.message}")
            10000
        }
    }
    // --- Cập nhật Target (Gọi từ Settings/UserViewModel) ---
    suspend fun updateTargetSteps(userId: String, newTarget: Int) {
        val todayStr = LocalDate.now().toString()
        // Chỉ cần update vào ngày hôm nay.
        // Ngày mai hàm syncHealthData sẽ tự động query ra ngày hôm nay (là ngày gần nhất) để kế thừa.
        val updateData = mapOf("targetSteps" to newTarget)

        firestore.collection("users").document(userId)
            .collection("daily_health").document(todayStr)
            .set(updateData, SetOptions.merge())
            .await()
    }

    private suspend fun syncAndCleanStepRecords(userId: String, start: LocalDateTime, end: LocalDateTime) {
        //Lấy tất cả bản ghi từ Health Connect (Nguồn gốc)
        val hcRecords = healthConnectManager.readRawStepRecords(start, end)

        //Lấy tất cả bản ghi từ Firestore (Bản sao)
        val startTimeMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimeMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = firestore.collection("users").document(userId)
            .collection("step_records")
            .whereGreaterThanOrEqualTo("startTime", startTimeMillis)
            .whereLessThanOrEqualTo("startTime", endTimeMillis)
            .get().await()

        val firestoreList = snapshot.toObjects(StepRecordEntity::class.java)

        for (hcRecord in hcRecords) {
            val hcStart = hcRecord.startTime.toEpochMilli()
            val hcCount = hcRecord.count.toInt()
            // Lấy package name an toàn
            val packageName = try { hcRecord.metadata.dataOrigin.packageName } catch (e: Exception) { "unknown" }

            // Kiểm tra xem record này đã có trong Firestore chưa
            val exists = firestoreList.any { fsRecord ->
                isSameRecord(fsRecord, hcStart, hcCount, packageName)
            }

            if (!exists) {
                val newRecord = StepRecordEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    startTime = hcStart,
                    endTime = hcRecord.endTime.toEpochMilli(),
                    count = hcCount,
                    source = packageName
                )
                firestore.collection("users").document(userId)
                    .collection("step_records").document(newRecord.id)
                    .set(newRecord).await()
                Log.d("Sync", "Đã thêm mới: $packageName - $hcCount")
            }
        }

        // CLEANUP (Xóa cái thừa thãi/đã bị xóa ở nguồn) ---
        // Duyệt qua Firestore, nếu cái nào KHÔNG tìm thấy bên Health Connect -> XÓA
        for (fsRecord in firestoreList) {
            val existsInHc = hcRecords.any { hcRecord ->
                val hcStart = hcRecord.startTime.toEpochMilli()
                val hcCount = hcRecord.count.toInt()
                val packageName = try { hcRecord.metadata.dataOrigin.packageName } catch (e: Exception) { "unknown" }

                isSameRecord(fsRecord, hcStart, hcCount, packageName)
            }

            // Nếu record trên FB không còn tồn tại bên HC (nghĩa là user đã xóa bên kia)
            if (!existsInHc) {
                firestore.collection("users").document(userId)
                    .collection("step_records").document(fsRecord.id)
                    .delete().await()
                Log.d("Sync", "Đã xóa record rác: ${fsRecord.source} - ${fsRecord.count}")
            }
        }
    }

    private suspend fun syncAndCleanHeartRecords(userId: String, start: LocalDateTime, end: LocalDateTime) {
        // 1. Đọc từ Health Connect
        val hcRecords = healthConnectManager.readRawHeartRecords(start, end)

        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 2. Đọc từ Firestore
        val snapshot = firestore.collection("users").document(userId)
            .collection("heart_rate_records")
            .whereGreaterThanOrEqualTo("time", startMillis)
            .whereLessThanOrEqualTo("time", endMillis)
            .get().await()

        val fsRecords = snapshot.toObjects(HeartRateRecordEntity::class.java)

        // PHA 1: IMPORT (HC -> FB)
        for (hcItem in hcRecords) {
            val itemTime = hcItem.startTime.toEpochMilli()
            val itemBpm = hcItem.samples.firstOrNull()?.beatsPerMinute?.toInt() ?: 0
            if (itemBpm == 0) continue

            val pkgName = try { hcItem.metadata.dataOrigin.packageName } catch (e: Exception) { "unknown" }

            // Check trùng (BPM bằng nhau và thời gian lệch < 2s)
            val exists = fsRecords.any { fs ->
                fs.bpm == itemBpm && kotlin.math.abs(fs.time - itemTime) < 2000
            }

            if (!exists) {
                val newRecord = HeartRateRecordEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    time = itemTime,
                    bpm = itemBpm,
                    source = pkgName
                )
                firestore.collection("users").document(userId)
                    .collection("heart_rate_records")
                    .document(newRecord.id)
                    .set(newRecord).await()
            }
        }

        // PHA 2: CLEANUP (FB -> Xóa nếu HC mất)
        for (fsItem in fsRecords) {
            val existsInHc = hcRecords.any { hcItem ->
                val itemTime = hcItem.startTime.toEpochMilli()
                val itemBpm = hcItem.samples.firstOrNull()?.beatsPerMinute?.toInt() ?: 0
                val pkgName = try { hcItem.metadata.dataOrigin.packageName } catch (e: Exception) { "unknown" }

                // So sánh kỹ cả source để tránh xóa nhầm của app khác nếu chưa kịp sync
                fsItem.bpm == itemBpm &&
                        kotlin.math.abs(fsItem.time - itemTime) < 2000 &&
                        (fsItem.source.isEmpty() || fsItem.source == pkgName)
            }

            if (!existsInHc) {
                firestore.collection("users").document(userId)
                    .collection("heart_rate_records").document(fsItem.id).delete().await()
            }
        }
    }
    private suspend fun syncAndCleanSleepSessions(userId: String, start: LocalDateTime, end: LocalDateTime) {
        // Đọc từ HC
        val hcRecords = healthConnectManager.readRawSleepRecords(start, end)

        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Đọc từ FB
        val snapshot = firestore.collection("users").document(userId)
            .collection("sleep_sessions")
            .whereGreaterThanOrEqualTo("startTime", startMillis)
            .whereLessThanOrEqualTo("startTime", endMillis)
            .get().await()

        val fsRecords = snapshot.toObjects(SleepSessionEntity::class.java)

        // IMPORT
        for (hcItem in hcRecords) {
            val sTime = hcItem.startTime.toEpochMilli()
            val eTime = hcItem.endTime.toEpochMilli()
            val pkg = try { hcItem.metadata.dataOrigin.packageName } catch (e: Exception) { "unknown" }

            val exists = fsRecords.any { fs ->
                kotlin.math.abs(fs.startTime - sTime) < 2000 &&
                        kotlin.math.abs(fs.endTime - eTime) < 2000
            }

            if (!exists) {
                val newRecord = SleepSessionEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    startTime = sTime,
                    endTime = eTime,
                    source = pkg
                )
                firestore.collection("users").document(userId)
                    .collection("sleep_sessions").document(newRecord.id).set(newRecord).await()
            }
        }

        //CLEANUP
        for (fs in fsRecords) {
            val existsInHc = hcRecords.any { hc ->
                val sTime = hc.startTime.toEpochMilli()
                val pkg = try { hc.metadata.dataOrigin.packageName } catch (e: Exception) { "unknown" }

                kotlin.math.abs(fs.startTime - sTime) < 2000 && (fs.source.isEmpty() || fs.source == pkg)
            }
            if (!existsInHc) {
                firestore.collection("users").document(userId)
                    .collection("sleep_sessions").document(fs.id).delete().await()
            }
        }
    }
    // Hàm phụ trợ: So sánh xem 2 record có phải là 1 không
    private fun isSameRecord(fsRecord: StepRecordEntity, hcStart: Long, hcCount: Int, hcSource: String): Boolean {
        //  Số bước phải bằng nhau
        if (fsRecord.count != hcCount) return false

        // Thời gian bắt đầu lệch không quá 2 giây (do sai số convert)
        val timeDiff = kotlin.math.abs(fsRecord.startTime - hcStart)
        if (timeDiff > 2000) return false

        //  Nguồn (Source) phải giống nhau (QUAN TRỌNG ĐỂ TRÁNH XÓA NHẦM)
        // Nếu record cũ chưa có source thì bỏ qua check source
        if (fsRecord.source.isNotEmpty() && fsRecord.source != hcSource) return false

        return true
    }    suspend fun updateLocalSteps(userId: String?, steps: Int, calories: Float) {
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

        val startTime = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTime = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val record = StepRecordEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startTime,
            endTime = endTime,
            count = stepsDelta,
            source = context.packageName
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
    suspend fun saveHeartRate(userId: String, bpm: Int, time: LocalDateTime) {

        val success = healthConnectManager.writeHeartRate(bpm,time)

        if (success) {
            val startOfDay = time.toLocalDate().atStartOfDay()
            val endOfDay = LocalDateTime.now()

            // TÍNH TOÁN LẠI TRUNG BÌNH CỘNG TRONG NGÀY
            val avgBpm = healthConnectManager.readHeartRate(startOfDay, endOfDay)

            val todayStr = time.toLocalDate().toString()
            val dailyUpdate = mapOf(
                "date" to todayStr,
                "userId" to userId,
                "heartRateAvg" to avgBpm
            )
            firestore.collection("users").document(userId)
                .collection("daily_health").document(todayStr)
                .set(dailyUpdate, SetOptions.merge())

            val timeMilli =time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // Lưu log chi tiết bản ghi
            val record = HeartRateRecordEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                time = timeMilli,
                bpm = bpm,
                source = context.packageName
            )
            firestore.collection("users").document(userId)
                .collection("heart_rate_records").document(record.id)
                .set(record)
                .await()
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
            type = "Sleep",
            source = context.packageName
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

        //  Fallback về Firestore (Nếu không có Health Connect)
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


    //-- AVG ---
    // Tính tổng bước chân
    private suspend fun recalculateDailySteps(userId: String, dateStr: String, start: LocalDateTime, end: LocalDateTime) {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = firestore.collection("users").document(userId)
            .collection("step_records")
            .whereGreaterThanOrEqualTo("startTime", startMillis)
            .whereLessThanOrEqualTo("startTime", endMillis)
            .get().await()

        val totalSteps = snapshot.toObjects(StepRecordEntity::class.java).sumOf { it.count }

        firestore.collection("users").document(userId)
            .collection("daily_health").document(dateStr)
            .set(mapOf("steps" to totalSteps), SetOptions.merge())
    }

    // Tính trung bình nhịp tim
    private suspend fun recalculateDailyHeartRate(userId: String, dateStr: String, start: LocalDateTime, end: LocalDateTime) {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = firestore.collection("users").document(userId)
            .collection("heart_rate_records")
            .whereGreaterThanOrEqualTo("time", startMillis)
            .whereLessThanOrEqualTo("time", endMillis)
            .get().await()

        val records = snapshot.toObjects(HeartRateRecordEntity::class.java)
        val avg = if (records.isNotEmpty()) records.map { it.bpm }.average().toInt() else 0

        firestore.collection("users").document(userId)
            .collection("daily_health").document(dateStr)
            .set(mapOf("heartRateAvg" to avg), SetOptions.merge())
    }

    // Tính tổng giờ ngủ
    private suspend fun recalculateDailySleep(userId: String, dateStr: String, start: LocalDateTime, end: LocalDateTime) {
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = firestore.collection("users").document(userId)
            .collection("sleep_sessions")
            .whereGreaterThanOrEqualTo("startTime", startMillis)
            .whereLessThanOrEqualTo("startTime", endMillis)
            .get().await()

        val totalMinutes = snapshot.toObjects(SleepSessionEntity::class.java).sumOf {
            (it.endTime - it.startTime) / 60000
        }

        firestore.collection("users").document(userId)
            .collection("daily_health").document(dateStr)
            .set(mapOf("sleepHours" to totalMinutes), SetOptions.merge())
    }

}