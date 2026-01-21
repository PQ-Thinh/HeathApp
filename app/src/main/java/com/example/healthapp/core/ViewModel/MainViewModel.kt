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
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
// ------------------------------------------

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val healthDao: HealthDao
) : ViewModel() {

    private val THEME_KEY = booleanPreferencesKey("is_dark_mode")

    private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")


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

    fun registerUser(email: String, pass: String) {
        viewModelScope.launch {
            val newUser = UserEntity(
                name = "",
                age = null, // Default
                height = null,
                weight = null,
                email = email,
                password = pass,
                gender = "Other",
            )
            healthDao.saveUser(newUser)
            setIsLoggedIn(true)
        }
    }

    fun loginUser(email: String, pass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            healthDao.getUser().collect { user ->
                if (user != null && user.email == email && user.password == pass) {
                    setIsLoggedIn(true)
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        }
    }


}