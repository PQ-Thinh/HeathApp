package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "password") val password: String,
    @ColumnInfo(name = "age") val age: Int?,
    @ColumnInfo(name = "gender") val gender: String,
    @ColumnInfo(name = "height") val height: Float?, // cm
    @ColumnInfo(name = "weight") val weight: Float?, // kg
    @ColumnInfo(name = "bmi") val bmi: Float?,
    @ColumnInfo(name = "target_steps") val targetSteps: Int = 10000
)