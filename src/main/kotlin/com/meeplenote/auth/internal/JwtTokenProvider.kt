package com.meeplenote.auth.internal

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-expiry-minutes}") private val accessTokenExpiryMinutes: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun createAccessToken(userId: Long): String {
        val now = Instant.now()
        val expiry = now.plus(Duration.ofMinutes(accessTokenExpiryMinutes))
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact()
    }

    /** 파싱 실패(서명 불일치, 만료, 형식 오류) 시 null — 필터에서 인증 미설정으로 처리 */
    fun parseUserIdOrNull(token: String): Long? =
        try {
            val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
            claims.subject.toLong()
        } catch (ex: JwtException) {
            null
        } catch (ex: IllegalArgumentException) {
            null
        }
}
