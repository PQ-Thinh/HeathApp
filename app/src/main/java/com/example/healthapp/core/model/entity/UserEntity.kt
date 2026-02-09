package com.example.healthapp.core.model.entity

data class UserEntity(

    var id: String = "",
    var name: String = "",
    val email: String = "",
    var age: Int? = null,
    var gender: String = "Male",
    var height: Float? = null,
    var weight: Float? = null,
    var bmi: Float? = null,
    var updatedAt: Long = System.currentTimeMillis()
) {

    constructor() : this("","", "", null, "Male", null, null, null)
}