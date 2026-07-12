package com.meeplenote.game.api

data class GameSummary(
    val id: Long,
    val nameKo: String?,
    val nameEn: String?,
)

interface GameLookup {
    fun getSummary(gameId: Long): GameSummary
}
