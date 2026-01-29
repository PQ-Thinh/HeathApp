package com.example.healthapp.core.viewmodel


import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class UserViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao,
    private val repository: HealthRepository
) : ViewModel() {

    private val THEME_KEY = booleanPreferencesKey("is_dark_mode")
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")

    val isDarkMode: StateFlow<Boolean> = dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentUserInfo: StateFlow<UserEntity?> = dataStore.data
        .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
        .flatMapLatest { email ->
            if (email != null) healthDao.getUserFlowByEmail(email) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch { dataStore.edit { it[THEME_KEY] = isDark } }
    }

    fun addName(name: String) {
        viewModelScope.launch {
            // Lấy user hiện tại từ Flow
            val user = currentUserInfo.filterNotNull().first()
            if (user != null && user.id != null) {
                Log.d("UserViewModel", "Đang lưu tên '$name' cho User ID: ${user.id}")
                healthDao.updateName(user.id, name)
            } else {
                Log.e("UserViewModel", "LỖI: Chưa tải xong UserInfo, không thể lưu tên!")
                // Mẹo: Nếu null, có thể thử delay 500ms rồi thử lại
            }
            val updatedUser = healthDao.getUserById(user.id)
            if (updatedUser != null) {
                repository.syncUserToCloud(updatedUser)
            }
        }
    }

    fun addHeight(height: Int) {
        viewModelScope.launch {
            val user = currentUserInfo.first()
            user?.id?.let { id ->
                healthDao.updateHeight(id, height.toFloat())
                calculateAndSaveBMI(id)
                val updatedUser = healthDao.getUserById(id)

                if (updatedUser != null) {
                    repository.syncUserToCloud(updatedUser)
                }
            }
        }
    }

    fun addWeight(weight: Float) {
        viewModelScope.launch {
            val user = currentUserInfo.first()
            user?.id?.let { id ->
                healthDao.updateWeight(id, weight)
                calculateAndSaveBMI(id)
                val updatedUser = healthDao.getUserById(id)

                if (updatedUser != null) {
                    repository.syncUserToCloud(updatedUser)
                }
            }

        }
    }

    private suspend fun calculateAndSaveBMI(userId: String) {
        val user = healthDao.getUserById(userId)
        if (user != null && user.height != null && user.weight != null && user.height > 0) {
            val heightInMeter = user.height / 100f
            val bmiValue = user.weight / (heightInMeter * heightInMeter)
            val bmiRounded = (bmiValue * 10).roundToInt() / 10f
            healthDao.updateBMI(userId, bmiRounded)
        }
    }
}