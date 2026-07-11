package com.meeplenote.auth.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtTokenProviderTest {

    private val secret = "test-secret-key-at-least-32-bytes-long-for-hs256"

    @Test
    fun `발급한 access 토큰에서 userId를 그대로 복원한다`() {
        val provider = JwtTokenProvider(secret, accessTokenExpiryMinutes = 30)

        val token = provider.createAccessToken(userId = 42L)

        assertThat(provider.parseUserIdOrNull(token)).isEqualTo(42L)
    }

    @Test
    fun `만료된 토큰은 null을 반환한다`() {
        val provider = JwtTokenProvider(secret, accessTokenExpiryMinutes = -1)

        val token = provider.createAccessToken(userId = 42L)

        assertThat(provider.parseUserIdOrNull(token)).isNull()
    }

    @Test
    fun `다른 시크릿으로 서명된 토큰은 null을 반환한다`() {
        val issuer = JwtTokenProvider(secret, accessTokenExpiryMinutes = 30)
        val verifier = JwtTokenProvider("another-secret-key-at-least-32-bytes-long!!", accessTokenExpiryMinutes = 30)

        val token = issuer.createAccessToken(userId = 42L)

        assertThat(verifier.parseUserIdOrNull(token)).isNull()
    }

    @Test
    fun `형식이 잘못된 토큰은 null을 반환한다`() {
        val provider = JwtTokenProvider(secret, accessTokenExpiryMinutes = 30)

        assertThat(provider.parseUserIdOrNull("not-a-jwt")).isNull()
    }
}
