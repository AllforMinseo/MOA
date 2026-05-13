package com.example.a20260310.data.model

data class ActionItem(
    val id: Int,
    val meetingId: Int,
    var title: String,
    var owner: String = "",
    var deadline: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)