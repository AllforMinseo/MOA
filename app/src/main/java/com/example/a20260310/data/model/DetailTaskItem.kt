package com.example.a20260310.data.model

/** 회의 상세 RecyclerView용 할 일 행 (서버 `task` / `assignee` / `due_date`와 대응). */
data class DetailTaskItem(
    val id: Int,
    val meetingId: Int,
    val title: String,
    val owner: String,
    val deadline: String,
)
