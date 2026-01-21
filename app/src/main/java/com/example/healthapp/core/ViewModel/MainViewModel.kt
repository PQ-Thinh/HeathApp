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
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
// ------------------------------------------

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao,
    private val repository: HealthRepository,
    private val sensorManager: HealthSensorManager
) : ViewModel() {

    private val THEME_KEY = booleanPreferencesKey("is_dark_mode")

    private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")

    private val _realtimeSteps = MutableStateFlow(0)
    val realtimeSteps: StateFlow<Int> = _realtimeSteps.asStateFlow()

    private val _realtimeHeartRate = MutableStateFlow(0)
    val realtimeHeartRate: StateFlow<Int> = _realtimeHeartRate.asStateFlow()

    init {
        startSensorTracking()
    }
    private fun startSensorTracking() {
        viewModelScope.launch {
            sensorManager.stepFlow.collect { steps ->
                // Lưu ý: Đây là số bước tính từ lúc khởi động máy (Since Boot)
                // Để tính số bước trong ngày, bạn cần trừ đi số bước tại thời điểm 00:00 sáng nay
                // (Logic trừ này ta sẽ làm ở bước nâng cao, giờ cứ hiển thị số raw trước)
                _realtimeSteps.value = steps
            }
        }

        viewModelScope.launch {
            sensorManager.heartRateFlow.collect { bpm ->
                _realtimeHeartRate.value = bpm
            }
        }
    }
    val isDarkMode: StateFlow<Boolean> = dataStore.data
        .map { preferences ->
            // Mặc định là false (Light Mode) nếu chưa lưu
            preferences[THEME_KEY] ?: false
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    val isLoggedIn: StateFlow<Boolean> = dataStore.data
        .map { it[IS_LOGGED_IN_KEY]?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )


    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch {
            dataStore.edit { mutablePreferences ->
                mutablePreferences[THEME_KEY] = isDark
            }
        }
    }
    fun setIsLoggedIn(isLoggedIn: Boolean) {
        viewModelScope.launch {
            dataStore.edit {
                it[IS_LOGGED_IN_KEY] = isLoggedIn
            }
        }
    }

    fun registerUser(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            // Kiểm tra email đã tồn tại chưa
            val existingUser = healthDao.getUserByEmail(email)
            if (existingUser != null) {
                onError("Email này đã được sử dụng!")
                return@launch
            }

            // Nếu chưa, tạo User mới
            val newUser = UserEntity(
                name = null,
                email = email,
                password = pass,
                age = null,
                height = null,
                weight = null,
                gender = "Male"
            )
            healthDao.saveUser(newUser)

            setIsLoggedIn(true)
            onSuccess()
        }
    }

    fun loginUser(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val user = healthDao.getUserByEmail(email)
            if (user != null && user.password == pass) {

                setIsLoggedIn(true)
                onSuccess()
            } else {
                setIsLoggedIn(false)
                onError("Email hoặc mật khẩu không đúng!")
            }
        }
    }
    fun addName(name: String) {
        viewModelScope.launch {
            healthDao.updateName(name)
        }
    }

    fun syncData() {
        viewModelScope.launch {
            // Lấy User hiện tại (đang đăng nhập)
            // Giả sử bạn có hàm lấy current user hoặc ID user lưu trong DataStore
            val currentUser = healthDao.getUserByEmail("email_dang_nhap@gmail.com")

            currentUser?.let { user ->
                //  Đồng bộ dữ liệu cho User này
                repository.syncHealthData(user.id)
            }
        }
    }


}