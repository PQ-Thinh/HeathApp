package com.example.healthapp.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.SleepBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth // Thay DAO/DataStore bằng Auth
) : ViewModel() {

    // --- State: Thời lượng ngủ hôm nay ---
    private val _sleepDuration = MutableStateFlow<Long>(0)
    val sleepDuration = _sleepDuration.asStateFlow()

    private val _sleepAssessment = MutableStateFlow<String>("Chưa có dữ liệu")
    val sleepAssessment = _sleepAssessment.asStateFlow()

    // --- State: Biểu đồ ---
    private val _chartData = MutableStateFlow<List<SleepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    // --- State: Lịch sử (Sửa lỗi Flow/Suspend) ---
    private val _sleepHistory = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    val sleepHistory = _sleepHistory.asStateFlow()

    init {
        loadTodaySleepData()
        loadChartData()
        loadHistory() // Load lịch sử khi mở
    }

    //Load dữ liệu hôm nay (Realtime)
    private fun loadTodaySleepData() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.getDailyHealth(today, userId).collect { data ->
                if (data != null) {
                    _sleepDuration.value = data.sleepHours
                    evaluateSleep(data.sleepHours)
                }
            }
        }
    }

    // Hàm lưu giấc ngủ (Gộp logic xử lý ngày giờ)
    fun saveSleepTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            val now = LocalDate.now()

            // Tạo thời gian bắt đầu và kết thúc hôm nay
            var startDateTime = LocalDateTime.of(now, LocalTime.of(startHour, startMinute))
            var endDateTime = LocalDateTime.of(now, LocalTime.of(endHour, endMinute))

            // Logic qua đêm: Nếu giờ dậy < giờ ngủ (VD: Ngủ 23h -> Dậy 7h sáng hôm sau)
            if (endDateTime.isBefore(startDateTime)) {
                endDateTime = endDateTime.plusDays(1)
            }

            // Logic lùi ngày: Nếu giờ ngủ lớn hơn giờ hiện tại (VD: Bây giờ 10h sáng, nhập ngủ lúc 23h đêm)
            // -> Tức là 23h đêm HÔM QUA
            if (startDateTime.isAfter(LocalDateTime.now())) {
                startDateTime = startDateTime.minusDays(1)
                endDateTime = endDateTime.minusDays(1)
            }

            // Lưu vào Repository
            repository.saveSleepSession(userId, startDateTime, endDateTime)

            // Đợi 1 chút rồi reload lại dữ liệu để UI cập nhật
            delay(1000)
            loadHistory()
            loadChartData()
        }
    }

    //Load Lịch sử (Thay thế cho Flow cũ)
    private fun loadHistory() {
        viewModelScope.launch {
            _sleepHistory.value = repository.getSleepHistory()
        }
    }

    // Load Biểu đồ
    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val data = repository.getSleepChartData(_selectedTimeRange.value)
            _chartData.value = data
        }
    }

    //Helper & Đánh giá
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

    // gộp logic vào saveSleepTime ở trên nên có thể bỏ hoặc để alias
    fun saveManualSleepSession(h1: Int, m1: Int, h2: Int, m2: Int) {
        saveSleepTime(h1, m1, h2, m2)
    }
}