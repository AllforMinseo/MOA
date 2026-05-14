package com.example.a20260310.data.model

data class MeetingFileRow(
    val title: String,
    val subtitle: String,
    val localPath: String,
    val displayName: String,
    val type: Type,
    /** 비어 있지 않으면 서버 파일(Authorization 필요 시 헤더로 다운로드). */
    val downloadUrl: String? = null,
) {
    enum class Type {
        AUDIO, IMAGE, PDF, DOCUMENT
    }
}