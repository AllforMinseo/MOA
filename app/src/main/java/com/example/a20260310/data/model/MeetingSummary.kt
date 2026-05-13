package com.example.a20260310.data.model

data class MeetingSummary(
    val id: Int,
    val meetingId: Int,
    val summary: String,
    val decisions: List<DecisionItem> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

//data class ActionItem(
//    val id: String = java.util.UUID.randomUUID().toString(),
//    var title: String,
//    var owner: String = "",
//    var deadline: String = "",
//)