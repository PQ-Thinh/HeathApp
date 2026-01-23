package com.example.healthapp.core.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.snapshotFlow
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
import androidx.health.connect.client.HealthConnectClient
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.roundToInt

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao,
    private val repository: HealthRepository,
    private val sensorManager: HealthSensorManager,
    val healthConnectManager: HealthConnectManager
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

    private val _chartData = MutableStateFlow<List<HeartRateBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()
    // Biến nội bộ
    private var startOfDaySteps = 0
    private var currentUserId: Int? = null // Default ID
    // Thêm State để báo lỗi ra UI
    private val _healthConnectState = MutableStateFlow<Int>(0)
    // 0: Init, 1: Available, 2: Update Required, 3: Not Supported
    val healthConnectState = _healthConnectState.asStateFlow()

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
                Log.d("MainViewModel", "User ID: $currentUserId")
            }
            currentUserId?.let { id ->
                val today = LocalDate.now().toString()
                val todayData = healthDao.getDailyHealth(today, id).firstOrNull()

                // Nếu hôm nay đã có dữ liệu, cập nhật lại State ngay lập tức
                if (todayData != null) {
                    // Khôi phục nhịp tim
                    if (todayData.heartRateAvg > 0) {
                        _realtimeHeartRate.value = todayData.heartRateAvg
                    }


                    _realtimeCalories.value = todayData.caloriesBurned


                    _realtimeSteps.value = todayData.steps
                }
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
    fun checkHealthConnectStatus() { // Đổi tên hàm cho đúng nghĩa
        viewModelScope.launch {
            val status = healthConnectManager.checkAvailability()

            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (healthConnectManager.hasAllPermissions()) {
                        syncData()
                        _healthConnectState.value = 1 // Đã ngon
                    } else {
                        _healthConnectState.value = 4 // Cần xin quyền
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    _healthConnectState.value = 2 // Cần update
                }
                else -> {
                    _healthConnectState.value = 3 // Không hỗ trợ
                }
            }
            Log.d("MainViewModel", " Gía trị chuyển: $_healthConnectState ")

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

                // Tính số bước thực tế trong ngày
                // Công thức: Tổng hiện tại - Mốc đầu ngày
                var todaySteps = totalStepsSinceBoot - startOfDaySteps
                if (todaySteps < 0) todaySteps = 0 // Tránh số âm

                // Cập nhật UI
                _realtimeSteps.value = todaySteps
                _realtimeCalories.value = todaySteps * 0.04f

                // 4. Lưu vào Room (Database)
                repository.updateLocalSteps(currentUserId, todaySteps, _realtimeCalories.value)
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

    suspend fun setIsLoggedIn(isLoggedIn: Boolean, email: String? = null) {
        dataStore.edit {
            it[IS_LOGGED_IN_KEY] = isLoggedIn
            if (email != null) it[CURRENT_USER_EMAIL_KEY] = email
        }
    }

    fun updateLoginStatus(isLoggedIn: Boolean) {
        viewModelScope.launch {
            setIsLoggedIn(isLoggedIn)
        }
    }

    fun registerUser(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val existingUser = healthDao.getUserByEmail(email)
            if (existingUser != null) {
                onError("Email này đã được sử dụng!")
                return@launch
            }

            val newUser = UserEntity(
                name = "New User",
                email = email,
                password = pass,
                targetSteps = 10000,
                gender = "Male",
                bmi = 0f,
                height = null,
                weight = null,
                age = null

            )
            healthDao.saveUser(newUser)

            //  Lấy lại User vừa tạo để có ID chính xác (vì ID tự tăng)
            val createdUser = healthDao.getUserByEmail(email)

            if (createdUser != null) {
                currentUserId = createdUser.id

                setIsLoggedIn(true, email)

                // Reset biến đếm bước chân về 0 cho user mới
                startOfDaySteps = 0
                _realtimeSteps.value = 0
                _realtimeCalories.value = 0f

                // Xóa mốc cũ trong DataStore để tránh nó trừ đi số bước của user cũ
                saveDayOffset(0)

                // Gọi lại initializeData để thiết lập lại từ đầu
                initializeData()

                onSuccess()
            } else {
                onError("Lỗi tạo tài khoản")
            }
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
            currentUserId?.let { id ->
                healthDao.updateName(id, name)
                Log.d("MainViewModel", "Đã cập nhật tên cho User ID: $id")
            } ?: run {
                Log.e("MainViewModel", "Lỗi: Chưa xác định được User ID!")
            }
        }
    }

    private fun calculateAndSaveBMI() {
        viewModelScope.launch {
            currentUserId?.let { id ->

                val user = healthDao.getUserById(id)

                if (user != null && user.height != null && user.weight != null && user.height > 0) {


                    val heightInMeter = user.height / 100f
                    val bmiValue = user.weight / (heightInMeter * heightInMeter)

                    val bmiRounded = (bmiValue * 10).roundToInt() / 10f

                    healthDao.updateBMI(id, bmiRounded)

                    Log.d("BMI", "Đã cập nhật BMI mới: $bmiRounded")
                }
            }
        }
    }

    fun addHeight(height: Int) {
        viewModelScope.launch {
            currentUserId?.let { id ->
                healthDao.updateHeight(id, height.toFloat())
                calculateAndSaveBMI()
            }
        }
    }

    fun addWeight(weight: Float) {
        viewModelScope.launch {
            currentUserId?.let { id ->
                healthDao.updateWeight(id, weight)
                calculateAndSaveBMI()
            }
        }
    }

    val currentUserInfo: StateFlow<UserEntity?> = dataStore.data
        .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
        .flatMapLatest { email ->
            if (email != null) {
                healthDao.getUserFlowByEmail(email)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun syncData() {
        viewModelScope.launch {
            // Đồng bộ dữ liệu cho User hiện tại
            repository.syncHealthData(currentUserId)
        }
    }
    // Hàm này để cập nhật giá trị hiển thị Realtime lên Dashboard
    fun updateRealtimeHeartRate(bpm: Int) {
        _realtimeHeartRate.value = bpm

    }


    //Biểu đồ
    // 1. Luồng dữ liệu cho NGÀY HÔM NAY (Tự động cập nhật khi DB thay đổi)
    // Dùng flatMapLatest để khi currentUserId thay đổi, nó tự lấy data của user mới
    val todayHealthData: StateFlow<DailyHealthEntity?> = snapshotFlow { currentUserId }
        .flatMapLatest { userId ->
            if (userId != null) {
                val today = LocalDate.now().toString()
                repository.getDailyHealth(today, userId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 2. Luồng dữ liệu cho BIỂU ĐỒ (7 ngày gần nhất)
    val weeklyHealthData: StateFlow<List<DailyHealthEntity>> = snapshotFlow { currentUserId }
        .flatMapLatest { userId ->
            if (userId != null) {
                healthDao.getLast7DaysHealth(userId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Hàm gọi khi người dùng đo xong nhịp tim
    fun saveHeartRateRecord(bpm: Int) {
        viewModelScope.launch {
            currentUserId?.let { id ->
                repository.saveHeartRate(id, bpm) // Lưu vào HC -> Sync Room
                loadChartData() // Refresh biểu đồ
            }
        }
    }

    // 2. Hàm load dữ liệu biểu đồ
    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val data = repository.getHeartRateChartData(_selectedTimeRange.value)
            _chartData.value = data
        }
    }
}
// calories = step X chieu cao X can năng