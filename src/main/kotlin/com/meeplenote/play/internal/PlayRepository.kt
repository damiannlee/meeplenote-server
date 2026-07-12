package com.meeplenote.play.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PlayRepository : JpaRepository<PlayEntity, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: UUID): PlayEntity?
    fun countByUserIdAndGameId(userId: Long, gameId: Long): Int
}
