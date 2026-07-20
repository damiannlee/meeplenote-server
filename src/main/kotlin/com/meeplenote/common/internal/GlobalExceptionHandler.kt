package com.meeplenote.common.internal

import com.meeplenote.common.api.BusinessException
import com.meeplenote.common.api.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(ErrorResponse.of(ex.code, ex.message ?: ex.code, ex.detail))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("VALIDATION_FAILED", "입력값이 올바르지 않습니다", detail))
    }

    /** 필수 필드 누락 등으로 요청 본문을 JSON 객체로 못 만드는 경우 — Kotlin non-null 생성자 파라미터 검증이 @Valid보다 먼저 실패한다 */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedRequest(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("VALIDATION_FAILED", "요청 본문을 읽을 수 없습니다"))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("MISSING_PARAMETER", "필수 파라미터가 누락되었습니다: ${ex.parameterName}"))

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("MISSING_PARAMETER", "필수 파일이 누락되었습니다: ${ex.requestPartName}"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("MISSING_HEADER", "필수 헤더가 누락되었습니다: ${ex.headerName}"))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("INVALID_PARAMETER", "파라미터 형식이 올바르지 않습니다: ${ex.name}"))

    /** 매핑된 핸들러가 없는 경로 — 기본값으로는 static resource 처리로 흘러가 Exception 캐치올(500)로 잘못 떨어진다 */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", "요청한 경로를 찾을 수 없습니다"))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR", "서버 오류가 발생했습니다"))
    }
}
