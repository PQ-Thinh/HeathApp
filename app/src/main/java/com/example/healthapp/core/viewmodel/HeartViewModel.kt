package com.example.healthapp.core.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HeartViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao
) : ViewModel() {

    // --- State cho Chart ---
    private val _heartRateData = MutableStateFlow<List<HeartRateBucket>>(emptyList())
    val heartRateData = _heartRateData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    // --- State cho thông tin chung ---
    private val _latestHeartRate = MutableStateFlow<Int>(0)
    val latestHeartRate = _latestHeartRate.asStateFlow()

    private val _assessment = MutableStateFlow<String>("Chưa có dữ liệu")
    val assessment = _assessment.asStateFlow()

    // Lấy thông tin user hiện tại (để load dữ liệu realtime nếu cần)
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")
    private val currentUserInfo = dataStore.data
        .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
        .flatMapLatest { email ->
            if (email != null) healthDao.getUserFlowByEmail(email) else flowOf(null)
        }

    init {
        loadLatestHeartRate()
        loadChartData() // Load mặc định (WEEK)
    }

    // 1. Hàm chọn mốc thời gian (Gọi từ UI)
    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    // 2. Load dữ liệu biểu đồ từ Health Connect
    private fun loadChartData() {
        viewModelScope.launch {
            val data = repository.getHeartRateChartData(_selectedTimeRange.value)
            _heartRateData.value = data
        }
    }

    // 3. Load nhịp tim mới nhất trong ngày để hiển thị số to và đánh giá
    private fun loadLatestHeartRate() {
        viewModelScope.launch {
            val user = currentUserInfo.filterNotNull().first()
            user.id?.let { userId ->
                val today = LocalDate.now().toString()
                repository.getDailyHealth(today, userId).collect { data ->
                    if (data != null && data.heartRateAvg > 0) {
                        _latestHeartRate.value = data.heartRateAvg
                        evaluateHeartRate(data.heartRateAvg)
                    }
                }
            }
        }
    }

    // 4. Logic đánh giá nhịp tim
    private fun evaluateHeartRate(bpm: Int) {
        _assessment.value = when {
            bpm == 0 -> "Chưa có dữ liệu"
            bpm < 60 -> "Nhịp tim chậm (Thường gặp ở VĐV)"
            bpm in 60..100 -> "Bình thường (Khỏe mạnh)"
            bpm in 101..120 -> "Hơi cao (Cần nghỉ ngơi)"
            else -> "Cao (Nhịp tim nhanh)"
        }
    }
        // Hàm gọi khi người dùng đo xong nhịp tim
    fun saveHeartRateRecord(bpm: Int) {
        viewModelScope.launch {
            val user = currentUserInfo.filterNotNull().first()
            user.id?.let { id ->
                repository.saveHeartRate(id, bpm) // Lưu vào HC -> Sync Room
                loadChartData() // Refresh biểu đồ
            }
        }
    }
}