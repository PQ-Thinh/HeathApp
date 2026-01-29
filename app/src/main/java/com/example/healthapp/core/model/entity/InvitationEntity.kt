package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invitations")
data class InvitationEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name") val senderName: String,
    @ColumnInfo(name = "receiver_id") val receiverId: String,
    @ColumnInfo(name = "status") val status: String, // "PENDING", "ACCEPTED", "REJECTED"
    @ColumnInfo(name = "timestamp") val timestamp: Long
) {
    constructor() : this("", "", "", "", "PENDING", 0)
}