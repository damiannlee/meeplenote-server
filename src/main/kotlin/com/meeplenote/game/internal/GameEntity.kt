package com.meeplenote.game.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class GameSource {
    BGG,
    CUSTOM,
}

@Entity
@Table(name = "games")
class GameEntity(
    @Column(name = "bgg_id")
    val bggId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    val source: GameSource,
    @Column(name = "created_by_user_id")
    val createdByUserId: Long? = null,
    @Column(name = "name_ko", length = 200)
    var nameKo: String? = null,
    @Column(name = "name_en", length = 200)
    var nameEn: String? = null,
    @Column(name = "name_initials", length = 200)
    var nameInitials: String? = null,
    @Column(name = "thumbnail_url", length = 500)
    var thumbnailUrl: String? = null,
    @Column(name = "min_players")
    var minPlayers: Short? = null,
    @Column(name = "max_players")
    var maxPlayers: Short? = null,
    @Column(name = "playtime_minutes")
    var playtimeMinutes: Short? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
