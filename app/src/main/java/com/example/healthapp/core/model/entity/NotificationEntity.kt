package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],     // Cột id bên bảng User
            childColumns = ["user_id"], // Cột user_id bên bảng này
            onDelete = ForeignKey.CASCADE // Xóa User -> Xóa luôn Thông báo
        )
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,

    @ColumnInfo(name = "user_id", index = true)
    val userId: String,

    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val isRead: Boolean = false,

    @ColumnInfo(name = "related_id") val relatedId: String? = null
) {
    // Constructor cho Firebase (nếu cần)
    constructor() : this(null, "", "", "", 0, "", false, null)
}