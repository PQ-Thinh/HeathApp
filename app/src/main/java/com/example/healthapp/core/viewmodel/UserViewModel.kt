package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import java.time.Period

@HiltViewModel
class UserViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    //private val repository: HealthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val THEME_KEY = booleanPreferencesKey("is_dark_mode")


    //Quản lý Theme (Dark Mode)
    val isDarkMode: StateFlow<Boolean> = dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserInfo: StateFlow<UserEntity?> = auth.authStateChanges()
        .flatMapLatest { firebaseUser ->
            if (firebaseUser == null) {
                flowOf<UserEntity?>(null)
            } else {
                callbackFlow<UserEntity?> {
                    val listener = firestore.collection("users").document(firebaseUser.uid)
                        .addSnapshotListener { snapshot, e->
                            if (e != null) {
                                Log.w("UserViewModel", "Lỗi lắng nghe User: ${e.message}")
                                return@addSnapshotListener
                            }

                            val user = snapshot?.toObject(UserEntity::class.java)
                            trySend(user)
                        }

                    awaitClose { listener.remove() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // Toggle Theme
    fun toggleTheme(isDark: Boolean) {
        viewModelScope.launch { dataStore.edit { it[THEME_KEY] = isDark } }
    }

    //Cập nhật thông tin cơ bản: Tên, Giới tính, Tuổi
    fun updateUserInfo(name: String, gender: String, day: Int, month: Int, year: Int) {
        val uid = auth.currentUser?.uid ?: return

        try {
            // Tạo đối tượng ngày sinh
            val birthDate = LocalDate.of(year, month, day)
            val today = LocalDate.now()

            // Tính tuổi chính xác
            val age = Period.between(birthDate, today).years


            // chuỗi ngày sinh "YYYY-MM-DD" để sau này App tự tính lại tuổi mỗi năm
            val birthDateString = birthDate.toString()

            val updates = mapOf(
                "name" to name,
                "gender" to gender,
                "age" to age, // Tuổi chính xác
                "birthDate" to birthDateString,
                "updatedAt" to System.currentTimeMillis()
            )

            viewModelScope.launch {
                firestore.collection("users").document(uid)
                    .set(updates, SetOptions.merge())
            }
        } catch (e: Exception) {
            // Phòng trường hợp ngày tháng không hợp lệ (VD: 30/02)
            e.printStackTrace()
        }
    }


    // Trong UserViewModel.kt

    fun addHeight(height: Int) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Lấy dữ liệu mới nhất trực tiếp từ Firestore
                val snapshot = firestore.collection("users").document(uid).get().await()
                val currentWeight = snapshot.getDouble("weight")?.toFloat() ?: 0f

                val newBmi = calculateBMI(height.toFloat(), currentWeight)

                val updates = mapOf(
                    "height" to height.toFloat(),
                    "bmi" to newBmi,
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(uid)
                    .set(updates, SetOptions.merge()).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addWeight(weight: Float) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // Lấy dữ liệu mới nhất trực tiếp từ Firestore
                val snapshot = firestore.collection("users").document(uid).get().await()
                val currentHeight = snapshot.getDouble("height")?.toFloat() ?: 0f

                val newBmi = calculateBMI(currentHeight, weight)

                val updates = mapOf(
                    "weight" to weight,
                    "bmi" to newBmi,
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(uid)
                    .set(updates, SetOptions.merge()).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    // Hàm phụ trợ tính BMI
    private fun calculateBMI(heightCm: Float, weightKg: Float): Float {
        if (heightCm <= 0 || weightKg <= 0) return 0f
        val heightM = heightCm / 100f
        val bmi = weightKg / (heightM * heightM)
        return (bmi * 10).roundToInt() / 10f // Làm tròn 1 chữ số thập phân
    }
}
fun FirebaseAuth.authStateChanges() = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}