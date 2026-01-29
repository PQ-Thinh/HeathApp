package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = false)
    val id: String,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "age") val age: Int? = null,
    @ColumnInfo(name = "gender") val gender: String = "Male",
    @ColumnInfo(name = "height") val height: Float? = null,
    @ColumnInfo(name = "weight") val weight: Float? = null,
    @ColumnInfo(name = "bmi") val bmi: Float? = null,
    @ColumnInfo(name = "target_steps") val targetSteps: Int = 10000,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {

    constructor() : this("", null, "", null, "Male", null, null, null, 10000,0)
}