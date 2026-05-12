package com.example.a20260310.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserCreateRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
)

data class UserResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
)

data class TokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String = "bearer",
)
