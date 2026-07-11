package com.meeplenote.auth.internal

import com.meeplenote.common.api.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class AuthServiceTest {

    private val kakaoUserInfoClient = mock<KakaoUserInfoClient>()
    private val userRepository = mock<UserRepository>()
    private val refreshTokenRepository = mock<RefreshTokenRepository>()
    private val jwtTokenProvider = mock<JwtTokenProvider>()

    private val authService = AuthService(
        kakaoUserInfoClient,
        userRepository,
        refreshTokenRepository,
        jwtTokenProvider,
        refreshTokenExpiryDays = 30,
    )

    @Test
    fun `신규 유저는 카카오 닉네임으로 생성되고 isNewUser가 true다`() {
        val kakaoUser = KakaoUserInfoResponse(
            id = 999L,
            kakaoAccount = KakaoUserInfoResponse.KakaoAccount(KakaoUserInfoResponse.Profile("서연")),
        )
        whenever(kakaoUserInfoClient.fetchUserInfo("kakao-token")).thenReturn(kakaoUser)
        whenever(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "999")).thenReturn(null)
        whenever(userRepository.save(any())).thenAnswer { it.getArgument<UserEntity>(0) }
        whenever(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token")

        val result = authService.loginWithKakao("kakao-token")

        assertThat(result.isNewUser).isTrue()
        assertThat(result.accessToken).isEqualTo("access-token")
        assertThat(result.refreshToken).isNotBlank()

        val captor = argumentCaptor<UserEntity>()
        verify(userRepository).save(captor.capture())
        assertThat(captor.firstValue.providerId).isEqualTo("999")
        assertThat(captor.firstValue.nickname).isEqualTo("서연")

        verify(refreshTokenRepository).save(any())
    }

    @Test
    fun `기존 유저는 다시 생성되지 않고 isNewUser가 false다`() {
        val kakaoUser = KakaoUserInfoResponse(id = 999L)
        val existingUser = UserEntity(provider = AuthProvider.KAKAO, providerId = "999", nickname = "기존닉네임")
        whenever(kakaoUserInfoClient.fetchUserInfo("kakao-token")).thenReturn(kakaoUser)
        whenever(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "999")).thenReturn(existingUser)
        whenever(jwtTokenProvider.createAccessToken(any())).thenReturn("access-token")

        val result = authService.loginWithKakao("kakao-token")

        assertThat(result.isNewUser).isFalse()
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `유효한 refresh 토큰은 새 access 토큰을 발급한다`() {
        val stored = RefreshTokenEntity(userId = 7L, tokenHash = "irrelevant", expiresAt = Instant.now().plusSeconds(60))
        whenever(refreshTokenRepository.findByTokenHash(any())).thenReturn(stored)
        whenever(jwtTokenProvider.createAccessToken(7L)).thenReturn("new-access-token")

        val accessToken = authService.refreshAccessToken("raw-refresh-token")

        assertThat(accessToken).isEqualTo("new-access-token")
    }

    @Test
    fun `저장된 refresh 토큰이 없으면 TOKEN_EXPIRED 예외를 던진다`() {
        whenever(refreshTokenRepository.findByTokenHash(any())).thenReturn(null)

        val ex = assertThrows<BusinessException> { authService.refreshAccessToken("unknown-token") }

        assertThat(ex.code).isEqualTo("TOKEN_EXPIRED")
    }

    @Test
    fun `만료된 refresh 토큰은 TOKEN_EXPIRED 예외를 던진다`() {
        val expired = RefreshTokenEntity(userId = 7L, tokenHash = "irrelevant", expiresAt = Instant.now().minusSeconds(1))
        whenever(refreshTokenRepository.findByTokenHash(any())).thenReturn(expired)

        val ex = assertThrows<BusinessException> { authService.refreshAccessToken("expired-token") }

        assertThat(ex.code).isEqualTo("TOKEN_EXPIRED")
    }

    @Test
    fun `revoke된 refresh 토큰은 TOKEN_EXPIRED 예외를 던진다`() {
        val revoked = RefreshTokenEntity(userId = 7L, tokenHash = "irrelevant", expiresAt = Instant.now().plusSeconds(60))
        revoked.revokedAt = Instant.now()
        whenever(refreshTokenRepository.findByTokenHash(any())).thenReturn(revoked)

        val ex = assertThrows<BusinessException> { authService.refreshAccessToken("revoked-token") }

        assertThat(ex.code).isEqualTo("TOKEN_EXPIRED")
    }
}
