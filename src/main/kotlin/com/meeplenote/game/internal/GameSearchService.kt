package com.meeplenote.game.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class GameSummary(
    val id: Long,
    val bggId: Long?,
    val nameKo: String?,
    val nameEn: String?,
    val thumbnailUrl: String?,
    val minPlayers: Short?,
    val maxPlayers: Short?,
    val playtime: Short?,
    val source: String,
)

data class GameSearchResponse(
    val items: List<GameSummary>,
    val hasMore: Boolean,
) {
    companion object {
        fun of(items: List<GameSummary>, limit: Int) = GameSearchResponse(items = items, hasMore = items.size == limit)
    }
}

/**
 * Local-only search over games already known to this service (BGG cached or
 * user-registered custom games). Zero matches return an empty list rather
 * than an error — the client shows a "register manually" CTA in that case.
 * BGG on-demand lookup (ADR-003) is tracked separately until BGG API access
 * is resolved (see docs/adr/ADR-003 implementation note).
 */
@Service
class GameSearchService(
    private val gameRepository: GameRepository,
) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int): GameSearchResponse {
        val results = searchLocal(query, limit)
        return GameSearchResponse.of(results.map { it.toSummary() }, limit)
    }

    private fun searchLocal(query: String, limit: Int): List<GameEntity> =
        if (InitialConsonantExtractor.isInitialsOnly(query)) {
            gameRepository.searchByInitials(query, limit)
        } else {
            gameRepository.searchByName(query, limit)
        }

    private fun GameEntity.toSummary() = GameSummary(
        id = id,
        bggId = bggId,
        nameKo = nameKo,
        nameEn = nameEn,
        thumbnailUrl = thumbnailUrl,
        minPlayers = minPlayers,
        maxPlayers = maxPlayers,
        playtime = playtimeMinutes,
        source = source.name.lowercase(),
    )
}
