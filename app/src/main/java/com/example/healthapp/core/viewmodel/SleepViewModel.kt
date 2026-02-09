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

    private val _sleepAssessment = MutableStateFlow<String>("Chưa có dữ liệu")
    val sleepAssessment = _sleepAssessment.asStateFlow()

    private val _chartData = MutableStateFlow<List<SleepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    private val _sleepHistory = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    val sleepHistory = _sleepHistory.asStateFlow()

    private var realtimeJob: Job? = null

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
        _sleepAssessment.value = "Chưa có dữ liệu"
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
                    evaluateSleep(data.sleepHours)
                }
            }
        }
    }

    fun saveSleepTime(
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            //Tạo thời gian bắt đầu chính xác theo Ngày đã chọn
            var startDateTime = LocalDateTime.of(date, LocalTime.of(startHour, startMinute))

            //Tạo thời gian kết thúc
            var endDateTime = LocalDateTime.of(date, LocalTime.of(endHour, endMinute))

            // Xử lý logic qua đêm (Nếu giờ kết thúc nhỏ hơn giờ bắt đầu -> Là ngày hôm sau)
            // Ví dụ: Ngủ 23:00 (ngày 1) -> Dậy 07:00 (thì phải là ngày 2)
            // Hoặc: Ngủ 23:00 -> Dậy 01:00 sáng
            if (endDateTime.isBefore(startDateTime)) {
                endDateTime = endDateTime.plusDays(1)
            }

            // Lưu vào Repository
            repository.saveSleepSession(userId, startDateTime, endDateTime)

            // Refresh dữ liệu
            delay(500) // Đợi DB cập nhật một chút
            loadHistory()
            loadChartData()
        }
    }

    private fun loadHistory() {
        viewModelScope.launch { _sleepHistory.value = repository.getSleepHistory() }
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

    private fun evaluateSleep(minutes: Long) {
        val hours = minutes / 60.0
        _sleepAssessment.value = when {
            minutes == 0L -> "Chưa có dữ liệu"
            hours < 5 -> "Kém (Quá ít)"
            hours in 5.0..6.5 -> "Khá (Cần ngủ thêm)"
            hours in 6.5..9.0 -> "Tốt (Lý tưởng)"
            else -> "Ngủ nhiều (Cần vận động)"
        }
    }

    fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }
    // Hàm tiện ích format ngày giờ
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
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
    fun editSleepSession(oldRecord: SleepSessionEntity, newDate: LocalDate, sH: Int, sM: Int, eH: Int, eM: Int) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            //Xóa record cũ
            repository.deleteSleepSession(oldRecord)

            // Tính toán thời gian mới (Logic giống saveSleepTime)
            var startDateTime = LocalDateTime.of(newDate, LocalTime.of(sH, sM))
            var endDateTime = LocalDateTime.of(newDate, LocalTime.of(eH, eM))

            if (endDateTime.isBefore(startDateTime)) {
                endDateTime = endDateTime.plusDays(1)
            }

            // Lưu record mới
            repository.saveSleepSession(userId, startDateTime, endDateTime)

            delay(500)
            loadHistory()
            loadChartData()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun authStateChanges() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}