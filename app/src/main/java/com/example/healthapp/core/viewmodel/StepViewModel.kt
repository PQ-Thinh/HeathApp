package com.example.healthapp.core.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.healthapp.core.model.entity.StepRecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class StepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao
) : ViewModel() {

    private val _chartData = MutableStateFlow<List<StepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    private var _startSessionSteps = 0 // Số bước tại thời điểm bấm Start
    private val _sessionSteps = MutableStateFlow(0) // Số bước chạy được trong phiên
    val sessionSteps = _sessionSteps.asStateFlow()
    private var _sessionStartTime = MutableStateFlow<LocalDateTime?>(null)
    // Cân nặng user (Mặc định 70kg nếu chưa set)
    private var userWeight: Float = 70f
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")
    val currentUserInfo = dataStore.data
        .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
        .flatMapLatest { email ->
            if (email != null) healthDao.getUserFlowByEmail(email) else flowOf(null)
        }

    init {
        loadUserProfile()
        loadChartData()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val email = dataStore.data.first()[stringPreferencesKey("current_user_email")]
            if (email != null) {
                val user = healthDao.getUserByEmail(email)
                userWeight = user?.weight ?: 70f
            }
        }
    }

    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val data = repository.getStepChartData(_selectedTimeRange.value)
            _chartData.value = data
        }
    }
    // Gọi hàm này khi màn hình RunTracking mở lên
    fun startRunSession(currentTotalSteps: Int) {
        _startSessionSteps = currentTotalSteps
        _sessionSteps.value = 0
        _sessionStartTime.value = LocalDateTime.now()
    }

    // Gọi hàm này liên tục khi sensor cập nhật
    fun updateSessionSteps(currentTotalSteps: Int) {
        // Nếu sensor reset (ví dụ reboot máy), cần reset mốc start
        if (currentTotalSteps < _startSessionSteps) {
            _startSessionSteps = 0
        }
        _sessionSteps.value = (currentTotalSteps - _startSessionSteps).coerceAtLeast(0)
    }
    // Công thức tính Calories cho Chart: 0.04 * steps * weight / 70
    fun calculateCalories(steps: Long): Int {
        return (0.04 * steps * userWeight / 70).toInt()
    }

    val stepHistory: StateFlow<List<StepRecordEntity>> = repository.getStepHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    fun finishRunSession(currentTotalSteps: Int) {
        val startTime = _sessionStartTime.value ?: return
        val endTime = LocalDateTime.now()

        // Tính số bước thực tế trong phiên
        val stepsTaken = (currentTotalSteps - _startSessionSteps).coerceAtLeast(0)

        if (stepsTaken > 0) {
            viewModelScope.launch {
                // Gọi Repository để lưu vào Room (bảng StepRecordEntity)
                // Hàm writeStepsToHealthConnect trong Repository của bạn đã có logic lưu vào Room rồi
                repository.writeStepsToHealthConnect(startTime, endTime, stepsTaken)
            }
        }

        // Reset lại trạng thái
        _sessionStartTime.value = null
        _startSessionSteps = 0
        _sessionSteps.value = 0
    }
}