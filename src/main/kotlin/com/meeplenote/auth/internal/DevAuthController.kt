package com.meeplenote.auth.internal

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val DEV_LOGIN_NICKNAME = "테스트유저"

/**
 * Manual local API testing only — mints a real JWT for a fixed dev user without a Kakao access token.
 * Only registered under the `dev-seed` profile (see docs/local-dev-testing.http), never in `local` alone
 * or in tests (test suite has no `@ActiveProfiles`, so it inherits the `local` default profile).
 */
@RestController
@Profile("dev-seed")
@RequestMapping("/api/v1/auth")
class DevAuthController(
    private val authService: AuthService,
) {
    @PostMapping("/dev-login")
    fun devLogin(): SocialLoginResponse {
        val result = authService.devLogin(DEV_LOGIN_NICKNAME)
        return SocialLoginResponse(result.accessToken, result.refreshToken, result.isNewUser)
    }
}
