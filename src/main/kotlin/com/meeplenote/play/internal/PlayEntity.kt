package com.meeplenote.play.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "plays")
class PlayEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "game_id", nullable = false)
    val gameId: Long,
    @Column(name = "played_at", nullable = false)
    val playedAt: LocalDate,
    @Column(name = "note")
    var note: String? = null,
    @Column(name = "rating")
    var rating: Short? = null,
    @Column(name = "photo_key", length = 300)
    var photoKey: String? = null,
    @Column(name = "idempotency_key")
    val idempotencyKey: UUID? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
