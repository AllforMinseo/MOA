package com.example.a20260310.data.remote

import com.example.a20260310.data.remote.dto.MeetingCreateRequest
import com.example.a20260310.data.remote.dto.ImageUploadResponseDto
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryGenerateResponseDto
import com.example.a20260310.data.remote.dto.SummaryUpdateRequestDto
import com.example.a20260310.data.remote.dto.TranscriptResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PATCH

/**
 * MOA FastAPI (backend/app/main.py) 엔드포인트.
 * - POST /meetings
 * - POST /upload/audio/{meeting_id}
 * - POST /meetings/{meeting_id}/summary
 */
interface MeetingApiService {
    @POST("meetings")
    suspend fun createMeeting(@Body body: MeetingCreateRequest): MeetingResponseDto

    @Multipart
    @POST("upload/audio/{meetingId}")
    suspend fun uploadAudioFiles(
        @Path("meetingId") meetingId: Int,
        @Part files: List<@JvmSuppressWildcards MultipartBody.Part>,
    ): TranscriptResponseDto

    @Multipart
    @POST("upload/image/{meetingId}")
    suspend fun uploadImageFiles(
        @Path("meetingId") meetingId: Int,
        @Part files: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part("image_type") imageType: RequestBody,
    ): List<ImageUploadResponseDto>

    @POST("meetings/{meetingId}/summary")
    suspend fun generateSummary(
        @Path("meetingId") meetingId: Int,
    ): SummaryGenerateResponseDto

    @GET("meetings/{meeting_id}/summary")
    suspend fun getMeetingSummary(
        @Path("meeting_id") meetingId: Int,
    ): SummaryGenerateResponseDto

    @PATCH("meetings/{meeting_id}/summary")
    suspend fun updateMeetingSummary(
        @Path("meeting_id") meetingId: Int,
        @Body request: SummaryUpdateRequestDto,
    ): SummaryGenerateResponseDto
}
