package com.example.healthapp.core.model.entity


data class InvitationEntity(
    var id: String="",
    val senderId: String="",
    val senderName: String="",
    val receiverId: String="",
    val status: String="PENDING",// ACCEPTED, REJECTED",
    val timestamp: Long,
    val targetSteps: Int = 0,
) {
    constructor() : this("", "", "", "", "", 0)
}