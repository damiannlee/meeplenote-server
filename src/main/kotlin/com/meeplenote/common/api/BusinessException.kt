package com.meeplenote.common.api

import org.springframework.http.HttpStatus

open class BusinessException(
    val code: String,
    message: String,
    val status: HttpStatus,
    val detail: Map<String, Any?> = emptyMap(),
) : RuntimeException(message)
