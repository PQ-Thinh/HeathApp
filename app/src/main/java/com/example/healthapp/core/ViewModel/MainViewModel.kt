package com.example.healthapp.core.ViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao,
    private val repository: HealthRepository,
    private val sensorManager: HealthSensorManager
) : ViewModel() {

    // --- KEYS DATASTORE ---
    private val THEME_KEY = booleanPreferencesKey("is_dark_mode")
    private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")

    // Key để lưu mốc bước chân đầu ngày (Dùng cho Hardware Sensor)
    private val START_OF_DAY_STEPS_KEY = intPreferencesKey("start_of_day_steps")
    private val LAST_SAVED_DATE_KEY = stringPreferencesKey("last_saved_date")

    // --- STATE FLOWS ---
    private val _realtimeSteps = MutableStateFlow(0)
    val realtimeSteps: StateFlow<Int> = _realtimeSteps.asStateFlow()

    // Thêm biến Calories bị thiếu
    private val _realtimeCalories = MutableStateFlow(0f)
    val realtimeCalories: StateFlow<Float> = _realtimeCalories.asStateFlow()

    private val _realtimeHeartRate = MutableStateFlow(0)
    val realtimeHeartRate: StateFlow<Int> = _realtimeHeartRate.asStateFlow()

    // Biến nội bộ
    private var startOfDaySteps = 0
    private var currentUserId: Int? = 1 // Default ID

    init {
        // 1. Load dữ liệu User và Mốc bước chân trước khi đo
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            // Lấy Email đang đăng nhập để tìm User ID
            val email = dataStore.data.first()[CURRENT_USER_EMAIL_KEY]
            if (email != null) {
                val user = healthDao.getUserByEmail(email)
                currentUserId = user?.id ?: 1
            }

            // Lấy Mốc bước chân đã lưu từ DataStore
            val preferences = dataStore.data.first()
            startOfDaySteps = preferences[START_OF_DAY_STEPS_KEY] ?: 0
            val savedDate = preferences[LAST_SAVED_DATE_KEY] ?: LocalDate.now().toString()

            // Kiểm tra xem có phải ngày mới không
            if (savedDate != LocalDate.now().toString()) {
                // Sang ngày mới -> Reset mốc = 0 (Sẽ cập nhật lại khi có dữ liệu sensor đầu tiên)
                startOfDaySteps = 0
                saveDayOffset(0)
            }

            // Bắt đầu lắng nghe cảm biến
            startSensorTracking()
        }
    }

    private fun startSensorTracking() {
        viewModelScope.launch {
            // 'totalStepsSinceBoot' là tổng số bước từ lúc bật điện thoại (VD: 15000)
            sensorManager.stepFlow.collect { totalStepsSinceBoot ->

                // 1. Xử lý logic Reset ngày mới hoặc Khởi tạo lần đầu
                val today = LocalDate.now().toString()
                val savedDate = dataStore.data.first()[LAST_SAVED_DATE_KEY]

                if (startOfDaySteps == 0 || savedDate != today) {
                    // Nếu chưa có mốc hoặc vừa sang ngày mới
                    // -> Lấy luôn số hiện tại làm mốc 00:00
                    startOfDaySteps = totalStepsSinceBoot
                    saveDayOffset(startOfDaySteps)
                }

                // Xử lý trường hợp Reboot máy (Tổng số bước sensor bị reset về 0)
                if (totalStepsSinceBoot < startOfDaySteps) {
                    startOfDaySteps = 0
                    saveDayOffset(0)
                }

                // 2. Tính số bước thực tế trong ngày
                // Công thức: Tổng hiện tại - Mốc đầu ngày
                var todaySteps = totalStepsSinceBoot - startOfDaySteps
                if (todaySteps < 0) todaySteps = 0 // Tránh số âm

                // 3. Cập nhật UI
                _realtimeSteps.value = todaySteps
                _realtimeCalories.value = todaySteps * 0.04f

                // 4. Lưu vào Room (Database)
                repository.updateLocalSteps(currentUserId, todaySteps, _realtimeCalories.value)
            }
        }

        viewModelScope.launch {
            sensorManager.heartRateFlow.collect { bpm ->
                _realtimeHeartRate.value = bpm
                // Lưu nhịp tim luôn nếu cần
                // healthDao.updateHeartRate(...)
            }
        }
    }

    // Hàm lưu Mốc và Ngày vào DataStore
    private suspend fun saveDayOffset(offset: Int) {
        dataStore.edit { prefs ->
            prefs[START_OF_DAY_STEPS_KEY] = offset
            prefs[LAST_SAVED_DATE_KEY] = LocalDate.now().toString()
        }
    }

    // --- CÁC HÀM QUẢN LÝ USER & LOGIN ---

    val isDarkMode: StateFlow<Boolean> = dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isLoggedIn: StateFlow<Boolean> = dataStore.data
        .map { it[IS_LOGGED_IN_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch { dataStore.edit { it[THEME_KEY] = isDark } }
    }

    fun setIsLoggedIn(isLoggedIn: Boolean, email: String? = null) {
        viewModelScope.launch {
            dataStore.edit {
                it[IS_LOGGED_IN_KEY] = isLoggedIn
                if (email != null) it[CURRENT_USER_EMAIL_KEY] = email
            }
        }
    }

    fun registerUser(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val existingUser = healthDao.getUserByEmail(email)
            if (existingUser != null) {
                onError("Email này đã được sử dụng!")
                return@launch
            }

            val newUser = UserEntity(
                name = "New User", // Tên tạm
                email = email,
                password = pass,
                age = null,
                height = null,
                weight = null,
                gender = "Male"
            )
            healthDao.saveUser(newUser)

            // Lưu trạng thái đăng nhập và email
            setIsLoggedIn(true, email)
            currentUserId = healthDao.getUserByEmail(email)?.id ?: 1
            onSuccess()
        }
    }

    fun loginUser(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = healthDao.getUserByEmail(email)
            if (user != null && user.password == pass) {
                setIsLoggedIn(true, email)
                currentUserId = user.id
                // Load lại dữ liệu sensor của user này
                initializeData()
                onSuccess()
            } else {
                setIsLoggedIn(false)
                onError("Email hoặc mật khẩu không đúng!")
            }
        }
    }



    fun addName(name: String) {
        viewModelScope.launch {

            healthDao.updateName(currentUserId, name)
        }
    }

    fun addHeight(height: Int) {
        viewModelScope.launch {
            healthDao.updateHeight(currentUserId, height.toFloat())
        }
    }

    fun addWeight(weight: Float) {
        viewModelScope.launch {
            healthDao.updateWeight(currentUserId, weight)
        }
    }

    fun syncData() {
        viewModelScope.launch {
            // Đồng bộ dữ liệu cho User hiện tại
            repository.syncHealthData(currentUserId)
        }
    }
}