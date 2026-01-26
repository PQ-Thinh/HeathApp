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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    // Cân nặng user (Mặc định 70kg nếu chưa set)
    private var userWeight: Float = 70f

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

    // Công thức tính Calories cho Chart: 0.04 * steps * weight / 70
    fun calculateCalories(steps: Long): Int {
        return (0.04 * steps * userWeight / 70).toInt()
    }
}