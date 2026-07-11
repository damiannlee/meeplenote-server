package com.meeplenote.auth.internal

import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?
}
