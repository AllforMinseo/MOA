package com.example.a20260310.data.remote

import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException

object ApiErrorParser {
    fun message(error: Throwable, fallback: String): String {
        if (error is HttpException) {
            return httpMessage(error, fallback)
        }

        return error.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun httpMessage(
        error: HttpException,
        fallback: String,
        includeCode: Boolean = false,
    ): String {
        val body = error.response()?.errorBody()?.string()?.trim().orEmpty()
        val parsed = parseBody(body) ?: fallback
        return if (includeCode) {
            "HTTP ${error.code()}: $parsed"
        } else {
            parsed
        }
    }

    private fun parseBody(body: String): String? {
        if (body.isBlank()) return null

        return runCatching {
            val json = JSONObject(body)
            when (val detail = json.opt("detail")) {
                is String -> detail.takeIf { it.isNotBlank() }
                is JSONArray -> parseValidationErrors(detail)
                null -> null
                else -> detail.toString().takeIf { it.isNotBlank() }
            }
        }.getOrNull() ?: body.takeIf { it.isNotBlank() }
    }

    private fun parseValidationErrors(errors: JSONArray): String? {
        val messages = mutableListOf<String>()
        for (index in 0 until errors.length()) {
            val item = errors.optJSONObject(index) ?: continue
            item.optString("msg").takeIf { it.isNotBlank() }?.let(messages::add)
        }

        return messages.joinToString("\n").takeIf { it.isNotBlank() }
    }
}
