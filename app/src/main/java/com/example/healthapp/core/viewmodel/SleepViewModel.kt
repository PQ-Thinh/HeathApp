package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.SleepBucket
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao
) : ViewModel() {

    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")

    private val currentUserInfo = dataStore.data
        .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
        .flatMapLatest { email ->
            if (email != null) healthDao.getUserFlowByEmail(email) else flowOf(null)
        }

    private val _sleepDuration = MutableStateFlow<Long>(0)
    val sleepDuration = _sleepDuration.asStateFlow()

    private val _sleepAssessment = MutableStateFlow<String>("Chưa có dữ liệu")
    val sleepAssessment = _sleepAssessment.asStateFlow()

    private val _chartData = MutableStateFlow<List<SleepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    init {
        loadTodaySleepData()
        loadChartData()
    }

    private fun loadTodaySleepData() {
        viewModelScope.launch {
            // Dùng biến currentUserInfo nội bộ vừa tạo
            val user = currentUserInfo.filterNotNull().first()
            user.id?.let { userId ->
                val today = LocalDate.now().toString()
                repository.getDailyHealth(today, userId).collect { data ->
                    if (data != null) {
                        _sleepDuration.value = data.sleepHours
                        evaluateSleep(data.sleepHours)
                    }
                }
            }
        }
    }

    fun saveSleepTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            val user = currentUserInfo.filterNotNull().first()
            val userId = user.id ?: return@launch

            val now = LocalDate.now()
            var startDateTime = LocalDateTime.of(now, LocalTime.of(startHour, startMinute))
            var endDateTime = LocalDateTime.of(now, LocalTime.of(endHour, endMinute))

            if (endDateTime.isBefore(startDateTime)) {
                endDateTime = endDateTime.plusDays(1)
            }

            if (startDateTime.isAfter(LocalDateTime.now())) {
                startDateTime = startDateTime.minusDays(1)
                endDateTime = endDateTime.minusDays(1)
            }

            repository.saveSleepSession(userId, startDateTime, endDateTime)
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
    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData() // Load lại dữ liệu ngay
    }
    // Hàm load dữ liệu
    fun loadChartData() {
        viewModelScope.launch {
            // Mặc định load 7 ngày (WEEK)
            val data = repository.getSleepChartData(_selectedTimeRange.value)
            _chartData.value = data
        }
    }
}