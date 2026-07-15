package com.meeplenote.game.api

data class GameSummary(
    val id: Long,
    val nameKo: String?,
    val nameEn: String?,
    val thumbnailUrl: String?,
)

interface GameLookup {
    fun getSummary(gameId: Long): GameSummary
    fun getSummaries(gameIds: Collection<Long>): List<GameSummary>
    fun findByBggId(bggId: Long): GameSummary?
    fun findCandidatesByName(name: String, limit: Int): List<GameSummary>
}
