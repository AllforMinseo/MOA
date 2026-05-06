package com.example.a20260310.data.model

data class Meeting(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val participants: List<String>,
    /** 이미지/PDF 등 업로드 API 응답의 `file_path`만 누적 (로컬 경로 아님) */
    val serverFilePaths: List<String>,
    val status: MeetingStatus,
    val summary: String? = null,
)

enum class MeetingStatus {
    CREATED,
    PROCESSING,
    COMPLETED,
    FAILED,
}
