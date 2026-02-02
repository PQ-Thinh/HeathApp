package com.example.healthapp.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HeartViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    // --- State cho Chart ---
    private val _heartRateData = MutableStateFlow<List<HeartRateBucket>>(emptyList())
    val heartRateData = _heartRateData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.DAY)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    // --- State cho thông tin chung ---
    private val _latestHeartRate = MutableStateFlow<Int>(0)
    val latestHeartRate = _latestHeartRate.asStateFlow()

    private val _assessment = MutableStateFlow<String>("Chưa có dữ liệu")
    val assessment = _assessment.asStateFlow()

    // --- State cho Lịch sử ---
    private val _heartHistory = MutableStateFlow<List<HeartRateRecordEntity>>(emptyList())
    val heartHistory = _heartHistory.asStateFlow()

    init {
        loadLatestHeartRate()
        loadChartData()
        loadHistory() // Load lịch sử khi mở màn hình
    }

    //Hàm chọn mốc thời gian
    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    //Load dữ liệu biểu đồ
    private fun loadChartData() {
        viewModelScope.launch {
            // Repository mới đã tự lo việc lấy UID bên trong hoặc trả về list rỗng
            val data = repository.getHeartRateChartData(_selectedTimeRange.value)
            _heartRateData.value = data
        }
    }

    //Load nhịp tim mới nhất (Realtime Flow từ Firestore)
    private fun loadLatestHeartRate() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.getDailyHealth(today, userId).collect { data ->
                if (data != null && data.heartRateAvg > 0) {
                    _latestHeartRate.value = data.heartRateAvg
                    evaluateHeartRate(data.heartRateAvg)
                }
            }
        }
    }

    //Load danh sách lịch sử chi tiết
    private fun loadHistory() {
        viewModelScope.launch {
            _heartHistory.value = repository.getHeartRateHistory()
        }
    }

    //Logic đánh giá nhịp tim
    private fun evaluateHeartRate(bpm: Int) {
        _assessment.value = when {
            bpm == 0 -> "Chưa có dữ liệu"
            bpm < 60 -> "Nhịp tim chậm (Thường gặp ở VĐV)"
            bpm in 60..100 -> "Bình thường (Khỏe mạnh)"
            bpm in 101..120 -> "Hơi cao (Cần nghỉ ngơi)"
            else -> "Cao (Nhịp tim nhanh)"
        }
    }

    //Lưu nhịp tim mới đo
    fun saveHeartRateRecord(bpm: Int) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            // Lưu vào Health Connect & Firestore
            repository.saveHeartRate(userId, bpm)

            // Cập nhật UI ngay lập tức
            _latestHeartRate.value = bpm
            evaluateHeartRate(bpm)

            //Đợi 1 chút để dữ liệu kịp đồng bộ rồi reload biểu đồ & lịch sử
            delay(1000)
            loadChartData()
            loadHistory()
        }
    }
}