package com.meeplenote.auth.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    fun isValid(now: Instant): Boolean = revokedAt == null && expiresAt.isAfter(now)
}
