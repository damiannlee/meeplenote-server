package com.meeplenote.play.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "play_players")
class PlayPlayerEntity(
    @Column(name = "play_id", nullable = false)
    val playId: Long,
    @Column(name = "player_id", nullable = false)
    val playerId: Long,
    @Column(name = "score")
    var score: Int? = null,
    @Column(name = "is_winner", nullable = false)
    var isWinner: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
