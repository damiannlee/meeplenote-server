package com.meeplenote.auth.internal

import com.fasterxml.jackson.annotation.JsonProperty
import com.meeplenote.common.api.BusinessException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SocialLoginRequest(
    @field:NotBlank val provider: String,
    @field:NotBlank val token: String,
)

data class SocialLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    @get:JsonProperty("isNewUser") val isNewUser: Boolean,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class RefreshResponse(
    val accessToken: String,
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/social")
    fun socialLogin(@RequestBody @Valid request: SocialLoginRequest): SocialLoginResponse {
        if (!request.provider.equals("kakao", ignoreCase = true)) {
            throw BusinessException(
                "UNSUPPORTED_PROVIDER",
                "지원하지 않는 provider입니다: ${request.provider}",
                HttpStatus.UNPROCESSABLE_ENTITY,
            )
        }
        val result = authService.loginWithKakao(request.token)
        return SocialLoginResponse(result.accessToken, result.refreshToken, result.isNewUser)
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid request: RefreshRequest): RefreshResponse {
        val accessToken = authService.refreshAccessToken(request.refreshToken)
        return RefreshResponse(accessToken)
    }
}
