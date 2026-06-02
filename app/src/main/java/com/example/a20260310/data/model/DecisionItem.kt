package com.example.a20260310.data.model

data class DecisionItem(
    val id: Int,
    val meetingId: Int,
    var content: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
