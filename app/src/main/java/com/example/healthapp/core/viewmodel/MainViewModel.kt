package com.example.healthapp.core.viewmodel

import android.app.ActivityManager
import android.content.Context
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
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
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
    //private val sensorManager: HealthSensorManager,
    val healthConnectManager: HealthConnectManager
) : ViewModel() {

    // --- KEYS DATASTORE ---
    //private val THEME_KEY = booleanPreferencesKey("is_dark_mode")
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

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()
    // Biến nội bộ
    private var startOfDaySteps = 0
    private var currentUserId: Int? = null // Default ID
    // Thêm State để báo lỗi ra UI
    private val _healthConnectState = MutableStateFlow<Int>(0)
    // 0: Init, 1: Available, 2: Update Required, 3: Not Supported
    val healthConnectState = _healthConnectState.asStateFlow()
    private val _todayHealthData = MutableStateFlow<DailyHealthEntity?>(null)
    val todayHealthData: StateFlow<DailyHealthEntity?> = _todayHealthData.asStateFlow()

    init {
        initializeData()
    // Bắt đầu lắng nghe Database ngay khi App mở
    }
    private fun initializeData() {
        viewModelScope.launch {
            // 1. Lấy User ID
            val email = dataStore.data.first()[CURRENT_USER_EMAIL_KEY]
            if (email != null) {
                val user = healthDao.getUserByEmail(email)
                currentUserId = user?.id ?: 1
            } else {
                currentUserId = 1 // Hoặc xử lý mặc định
            }

            // 2. Bắt đầu lắng nghe Database ngay sau khi có User ID
            // Logic này thay thế cho observeDatabase cũ và cả snapshotFlow
            currentUserId?.let { userId ->
                val today = LocalDate.now().toString()
                repository.getDailyHealth(today, userId).collect { data ->
                    // Cập nhật StateFlow nội bộ
                    _todayHealthData.value = data

                    // Cập nhật UI State (Logic cũ của observeDatabase)
                    if (data != null) {
                        _realtimeSteps.value = data.steps
                        _realtimeCalories.value = data.caloriesBurned
                        if (data.heartRateAvg > 0) {
                            _realtimeHeartRate.value = data.heartRateAvg
                        }
                    }
                }
            }
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


    // Hàm lưu Mốc và Ngày vào DataStore
    private suspend fun saveDayOffset(offset: Int) {
        dataStore.edit { prefs ->
            prefs[START_OF_DAY_STEPS_KEY] = offset
            prefs[LAST_SAVED_DATE_KEY] = LocalDate.now().toString()
        }
    }

    // --- CÁC HÀM QUẢN LÝ USER & LOGIN ---
    val isLoggedIn: StateFlow<Boolean> = dataStore.data
        .map { it[IS_LOGGED_IN_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)



    suspend fun setIsLoggedIn(isLoggedIn: Boolean, email: String? = null) {
        dataStore.edit {
            it[IS_LOGGED_IN_KEY] = isLoggedIn
            if (isLoggedIn && email != null) {
                it[CURRENT_USER_EMAIL_KEY] = email
            } else if (!isLoggedIn) {
                // KHI ĐĂNG XUẤT: Xóa sạch Email và Token để tránh nhớ nhầm User cũ
                it.remove(CURRENT_USER_EMAIL_KEY)
                currentUserId = null // Reset ID trong bộ nhớ
                _realtimeSteps.value = 0
                _realtimeCalories.value = 0f
            }
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
               // initializeData()

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

//    //Biểu đồ
//    //Luồng dữ liệu cho NGÀY HÔM NAY (Tự động cập nhật khi DB thay đổi)
//    // Dùng flatMapLatest để khi currentUserId thay đổi, nó tự lấy data của user mới
//    val todayHealthData: StateFlow<DailyHealthEntity?> = snapshotFlow { currentUserId }
//        .flatMapLatest { userId ->
//            if (userId != null) {
//                val today = LocalDate.now().toString()
//                repository.getDailyHealth(today, userId)
//            } else {
//                flowOf(null)
//            }
//        }
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setServiceRunningStatus(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }
    val currentMode: StateFlow<String> = dataStore.data
        .map { preferences ->
            preferences[stringPreferencesKey("current_mode")] ?: "Chạy Bộ"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Chạy Bộ"
        )
    // Hàm kiểm tra Service đang chạy

}
// calories = step X chieu cao X can năng
//    private fun initializeData() {
//        viewModelScope.launch {
//            // Lấy Email đang đăng nhập để tìm User ID
//            val email = dataStore.data.first()[CURRENT_USER_EMAIL_KEY]
//            if (email != null) {
//                val user = healthDao.getUserByEmail(email)
//                currentUserId = user?.id ?: 1
//                Log.d("MainViewModel", "User ID: $currentUserId")
//            }
////            currentUserId?.let { id ->
////                val today = LocalDate.now().toString()
////                val todayData = healthDao.getDailyHealth(today, id).firstOrNull()
////
////                // Nếu hôm nay đã có dữ liệu, cập nhật lại State ngay lập tức
////                if (todayData != null) {
////                    // Khôi phục nhịp tim
////                    if (todayData.heartRateAvg > 0) {
////                        _realtimeHeartRate.value = todayData.heartRateAvg
////                    }
////
////
////                    _realtimeCalories.value = todayData.caloriesBurned
////
////
////                    _realtimeSteps.value = todayData.steps
////                }
////            }
////            // Lấy Mốc bước chân đã lưu từ DataStore
////            val preferences = dataStore.data.first()
////            startOfDaySteps = preferences[START_OF_DAY_STEPS_KEY] ?: 0
////            val savedDate = preferences[LAST_SAVED_DATE_KEY] ?: LocalDate.now().toString()
////
////            // Kiểm tra xem có phải ngày mới không
////            if (savedDate != LocalDate.now().toString()) {
////                // Sang ngày mới -> Reset mốc = 0 (Sẽ cập nhật lại khi có dữ liệu sensor đầu tiên)
////                startOfDaySteps = 0
////                saveDayOffset(0)
////            }
//
//
//            //startSensorTracking()
//        }
//    }
//    fun startSensorTracking() {
//        viewModelScope.launch {
//            lastSyncTime = LocalDateTime.now()
//            // 'totalStepsSinceBoot' là tổng số bước từ lúc bật điện thoại (VD: 15000)
//            sensorManager.stepFlow.collect { totalStepsSinceBoot ->
//
//                // 1. Xử lý logic Reset ngày mới hoặc Khởi tạo lần đầu
//                val today = LocalDate.now().toString()
//                val savedDate = dataStore.data.first()[LAST_SAVED_DATE_KEY]
//
//                if (startOfDaySteps == 0 || savedDate != today) {
//                    // Nếu chưa có mốc hoặc vừa sang ngày mới
//                    // -> Lấy luôn số hiện tại làm mốc 00:00
//                    startOfDaySteps = totalStepsSinceBoot
//                    saveDayOffset(startOfDaySteps)
//                }
//
//                // Xử lý trường hợp Reboot máy (Tổng số bước sensor bị reset về 0)
//                if (totalStepsSinceBoot < startOfDaySteps) {
//                    startOfDaySteps = 0
//                    saveDayOffset(0)
//                }
//
//                // Tính số bước thực tế trong ngày
//                // Công thức: Tổng hiện tại - Mốc đầu ngày
//                var todaySteps = totalStepsSinceBoot - startOfDaySteps
//                if (todaySteps < 0) todaySteps = 0
//
//                // Cập nhật UI
//                _realtimeSteps.value = todaySteps
//                _realtimeCalories.value = todaySteps * 0.04f
//                // ĐỒNG BỘ LÊN HEALTH CONNECT (SỬA LỖI TẠI ĐÂY)
//                // Chỉ gửi khi số bước chia hết cho 50 (ví dụ: 50, 100, 150...) để tiết kiệm pin
//                // Hoặc gửi khi số bước thay đổi quá nhiều so với lần sync trước
//                if (todaySteps - lastSyncedStepsTotal >= 50) {
//                    val now = LocalDateTime.now()
//
//                    // Tính số bước chênh lệch: 150 - 100 = 50 bước mới
//                    val stepsToAdd = todaySteps - lastSyncedStepsTotal
//
//                    if (stepsToAdd > 0) {
//                        // Gọi hàm writeSteps mới (chỉ insert 50 bước trong khoảng thời gian từ lần trước đến nay)
//                        repository.writeStepsToHealthConnect(lastSyncTime, now, stepsToAdd)
//
//                        // Cập nhật lại mốc
//                        lastSyncedStepsTotal = todaySteps
//                        lastSyncTime = now
//                    }
//                }
//            }
//        }
//    }