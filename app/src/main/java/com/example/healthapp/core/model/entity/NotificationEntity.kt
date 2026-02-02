package com.example.healthapp.core.model.entity

import java.util.UUID


data class NotificationEntity(
    var id: String = UUID.randomUUID().toString(),
    var userId: String,
    val title: String,
    val message: String,
    var timestamp: Long = System.currentTimeMillis(),
    var type: String,
    var isRead: Boolean = false,
    val relatedId: String? = null
) {
    constructor() : this("", "", "", "", 0, "", false, null)
}