package com.example.a20260310.data.repository

import com.example.a20260310.data.remote.ApiClient
import com.example.a20260310.data.remote.MeetingApiService
import com.example.a20260310.data.remote.dto.ImageUploadResponseDto
import com.example.a20260310.data.remote.dto.MeetingCreateRequest
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryGenerateResponseDto
import com.example.a20260310.data.remote.dto.TranscriptResponseDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MeetingRepository(
    private val api: MeetingApiService = ApiClient.meetingApi,
) {
    suspend fun createMeeting(title: String, description: String?): MeetingResponseDto {
        return api.createMeeting(MeetingCreateRequest(title = title, description = description))
    }

    suspend fun uploadAudio(meetingId: Int, files: List<File>): TranscriptResponseDto {
        require(files.isNotEmpty()) { "uploadAudio requires at least one file" }
        val parts =
            files.map { file ->
                val mediaType = audioMediaTypeForFile(file.name).toMediaTypeOrNull()
                val body = file.asRequestBody(mediaType)
                MultipartBody.Part.createFormData("files", file.name, body)
            }
        return api.uploadAudio(meetingId, parts)
    }

    private fun audioMediaTypeForFile(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    suspend fun uploadImage(meetingId: Int, file: File, imageType: String = "image"): ImageUploadResponseDto {
        val mediaType = mediaTypeForUploadFile(file.name).toMediaTypeOrNull()
        val body = file.asRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        val imageTypeBody = imageType.toRequestBody("text/plain".toMediaType())
        return api.uploadImage(meetingId, part, imageTypeBody)
    }

    private fun mediaTypeForUploadFile(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    suspend fun generateSummary(meetingId: Int): SummaryGenerateResponseDto {
        return api.generateSummary(meetingId)
    }
}
