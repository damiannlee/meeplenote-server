package com.meeplenote.auth.internal

import com.meeplenote.common.api.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class SocialLoginResult(
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean,
)

@Service
class AuthService(
    private val kakaoUserInfoClient: KakaoUserInfoClient,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${jwt.refresh-token-expiry-days}") private val refreshTokenExpiryDays: Long,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun loginWithKakao(kakaoAccessToken: String): SocialLoginResult {
        val kakaoUser = kakaoUserInfoClient.fetchUserInfo(kakaoAccessToken)
        val providerId = kakaoUser.id.toString()

        val existing = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)
        val user = existing ?: userRepository.save(
            UserEntity(provider = AuthProvider.KAKAO, providerId = providerId, nickname = kakaoUser.nickname()),
        )

        val accessToken = jwtTokenProvider.createAccessToken(user.id)
        val refreshToken = issueRefreshToken(user.id)

        return SocialLoginResult(accessToken, refreshToken, isNewUser = existing == null)
    }

    @Transactional
    fun refreshAccessToken(refreshToken: String): String {
        val stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
            ?: throw tokenExpiredException()

        if (!stored.isValid(Instant.now())) {
            throw tokenExpiredException()
        }

        return jwtTokenProvider.createAccessToken(stored.userId)
    }

    private fun issueRefreshToken(userId: Long): String {
        val rawToken = generateOpaqueToken()
        val expiresAt = Instant.now().plus(Duration.ofDays(refreshTokenExpiryDays))
        refreshTokenRepository.save(
            RefreshTokenEntity(userId = userId, tokenHash = hash(rawToken), expiresAt = expiresAt),
        )
        return rawToken
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun tokenExpiredException() =
        BusinessException("TOKEN_EXPIRED", "refresh 토큰이 만료되었거나 유효하지 않습니다", HttpStatus.UNAUTHORIZED)
}
