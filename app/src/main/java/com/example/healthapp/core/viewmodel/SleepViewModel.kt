package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.SleepBucket
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.helperEnum.ChartTimeRange
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _sleepDuration = MutableStateFlow<Long>(0)
    val sleepDuration = _sleepDuration.asStateFlow()

//    private val _sleepAssessment = MutableStateFlow<String>("Chưa có dữ liệu")
//    val sleepAssessment = _sleepAssessment.asStateFlow()

    private val _chartData = MutableStateFlow<List<SleepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    private val _sleepHistory = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    val sleepHistory = _sleepHistory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private var realtimeJob: Job? = null
    private var historyJob: Job? = null

    init {
        viewModelScope.launch {
            authStateChanges().collectLatest { user ->
                if (user == null) {
                    clearData()
                } else {
                    loadDataForUser(user.uid)
                }
            }
        }
    }

    private fun clearData() {
       _sleepDuration.value = 0
        //_sleepAssessment.value = "Chưa có dữ liệu"
        _chartData.value = emptyList()
        _sleepHistory.value = emptyList()
        realtimeJob?.cancel()
    }

    private fun loadDataForUser(uid: String) {
        loadChartData()
        loadHistory()

        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.getDailyHealth(today, uid).collect { data ->
                if (data != null) {
                    _sleepDuration.value = data.sleepHours
//                    evaluateSleep(data.sleepHours)

                }
            }
        }
    }


    fun saveSleepWithStages(
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        stages: List<SleepStageInput> // Nhận danh sách từ UI
    ) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            //  Tính toán ngày giờ bắt đầu
            val startDateTime = LocalDateTime.of(date, LocalTime.of(startHour, startMinute))

            //  Cộng dồn thời gian từ danh sách
            var deep = 0L
            var rem = 0L
            var light = 0L
            var awake = 0L
            var totalMinutes = 0L

            stages.forEach {
                totalMinutes += it.durationMinutes
                when (it.type) {
                    "Deep", "Ngủ sâu (Deep)" -> deep += it.durationMinutes
                    "REM" -> rem += it.durationMinutes
                    "Light", "Ngủ nông (Light)" -> light += it.durationMinutes
                    "Awake", "Đã thức (Awake)" -> awake += it.durationMinutes
                }
            }

            //  Tính ngày giờ kết thúc
            val endDateTime = startDateTime.plusMinutes(totalMinutes)

            //Lưu vào Repository
            repository.saveSleepSessionWithDetails(
                userId = userId,
                start = startDateTime,
                end = endDateTime,
                deep = deep,
                rem = rem,
                light = light,
                awake = awake
            )
           loadChartData()
        }
    }

    private fun loadHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.getSleepHistory().collect { historyList ->
                _sleepHistory.value = historyList

                if (historyList.isNotEmpty()) {
                    loadChartData()
                }
            }
        }
    }

    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        viewModelScope.launch {
            _chartData.value = repository.getSleepChartData(_selectedTimeRange.value)
        }
    }


//    private fun evaluateSleep(minutes: Long) {
//        val hours = minutes / 60.0
//        _sleepAssessment.value = when {
//            minutes == 0L -> "Chưa có dữ liệu"
//            hours < 5 -> "Kém (Quá ít)"
//            hours in 5.0..6.5 -> "Khá (Cần ngủ thêm)"
//            hours in 6.5..9.0 -> "Tốt (Lý tưởng)"
//            else -> "Ngủ nhiều (Cần vận động)"
//        }
//    }
    fun evaluateSessionQuality(session: SleepSessionEntity): SleepQualityResult {
        val durationMillis = session.endTime - session.startTime
        val totalMinutes = durationMillis / 60000
        val totalHours = totalMinutes / 60.0

        // Ưu tiên 1: Đánh giá theo Stages (nếu có dữ liệu Deep/REM)
        if (session.hasDetailedStages()) {
            val totalSleep = (session.deepSleepDuration + session.remSleepDuration + session.lightSleepDuration).toDouble()
            // Tránh chia cho 0
            if (totalSleep > 0) {
                val restorative = session.deepSleepDuration + session.remSleepDuration
                val percentRestorative = (restorative / totalSleep) * 100

                return when {
                    percentRestorative >= 45 -> SleepQualityResult("Rất Tốt (Hồi phục cao)", 0xFF22C55E) // Xanh lá đậm
                    percentRestorative >= 25 -> SleepQualityResult("Tốt (Đủ độ sâu)", 0xFF10B981) // Xanh lá
                    session.awakeDuration > 60 -> SleepQualityResult("Chập chờn (Thức nhiều)", 0xFFF59E0B) // Vàng cam
                    else -> SleepQualityResult("Bình thường", 0xFF3B82F6) // Xanh dương
                }
            }
        }

        // Fallback về đánh giá theo tổng thời gian
        return when {
            totalHours < 5 -> SleepQualityResult("Kém (Quá ít)", 0xFFEF4444) // Đỏ
            totalHours in 5.0..6.5 -> SleepQualityResult("Khá (Cần ngủ thêm)", 0xFFF59E0B) // Vàng
            totalHours in 6.5..9.0 -> SleepQualityResult("Tốt (Lý tưởng)", 0xFF22C55E) // Xanh lá
            else -> SleepQualityResult("Ngủ nhiều", 0xFF3B82F6) // Xanh dương
        }
    }

    fun formatMinToHr(minutes: Long): String {
        if (minutes == 0L) return "0m"
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
    fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
    fun deleteSleepRecord(record: SleepSessionEntity) {
        //val uid = auth.currentUser?.uid ?: return

        // Xóa khỏi List hiển thị NGAY LẬP TỨC
        val currentList = _sleepHistory.value.toMutableList()
        currentList.remove(record)
        _sleepHistory.value = currentList

        viewModelScope.launch {
            try {
                //  Sau đó mới gọi xuống Database xóa ngầm
                repository.deleteSleepSession(record)
                // Load lại chart cho chuẩn số liệu
                loadChartData()
            } catch (e: Exception) {
                // Nếu lỗi thì load lại list cũ (hoàn tác)
                loadHistory()
                Log.e("HeartViewModel", "Xóa thất bại", e)
            }
        }
    }
    fun editSleepSessionWithStages(
        oldRecord: SleepSessionEntity,
        newDate: LocalDate,
        startHour: Int,
        startMinute: Int,
        stages: List<SleepStageInput>
    ) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.deleteSleepSession(oldRecord)

            val startDateTime = LocalDateTime.of(newDate, LocalTime.of(startHour, startMinute))

            var deep = 0L
            var rem = 0L
            var light = 0L
            var awake = 0L
            var totalMinutes = 0L

            stages.forEach {
                totalMinutes += it.durationMinutes
                when (it.type) {
                    "Deep", "Ngủ sâu (Deep)" -> deep += it.durationMinutes
                    "REM" -> rem += it.durationMinutes
                    "Light", "Ngủ nông (Light)" -> light += it.durationMinutes
                    "Awake", "Đã thức (Awake)" -> awake += it.durationMinutes
                }
            }

            val endDateTime = startDateTime.plusMinutes(totalMinutes)

            repository.saveSleepSessionWithDetails(
                userId = userId,
                start = startDateTime,
                end = endDateTime,
                deep = deep,
                rem = rem,
                light = light,
                awake = awake
            )
            loadChartData()
            loadHistory()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun authStateChanges() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val uid = auth.currentUser?.uid
            if (uid != null) {
                loadChartData()
                loadHistory()
                delay(500)
            }
            _isRefreshing.value = false
        }
    }
}
data class SleepQualityResult(val text: String, val colorHex: Long)
data class SleepStageInput(
    val type: String,
    val durationMinutes: Int,
    val color: androidx.compose.ui.graphics.Color
)