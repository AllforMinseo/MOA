package com.example.a20260310.data.remote

import com.example.a20260310.data.remote.dto.TokenResponseDto
import com.example.a20260310.data.remote.dto.UserCreateRequest
import com.example.a20260310.data.remote.dto.UserResponseDto
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/register")
    suspend fun signup(@Body body: UserCreateRequest): UserResponseDto

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): TokenResponseDto
}
