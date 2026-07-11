package com.meeplenote.common.api

data class ErrorResponse(
    val error: ErrorBody,
) {
    data class ErrorBody(
        val code: String,
        val message: String,
        val detail: Map<String, Any?> = emptyMap(),
    )

    companion object {
        fun of(code: String, message: String, detail: Map<String, Any?> = emptyMap()): ErrorResponse =
            ErrorResponse(ErrorBody(code, message, detail))
    }
}
