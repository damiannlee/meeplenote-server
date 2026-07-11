package com.meeplenote.game.internal

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Persists BGG lookup results into the local cache (games table), batched to avoid N+1. */
@Component
class GameCacheWriter(
    private val gameRepository: GameRepository,
) {
    @Transactional
    fun cacheAll(details: List<BggGameDetail>): List<GameEntity> {
        if (details.isEmpty()) return emptyList()

        val existingByBggId = gameRepository.findAllByBggIdIn(details.map { it.id }).associateBy { it.bggId }

        return details.map { detail -> existingByBggId[detail.id] ?: insert(detail) }
    }

    private fun insert(detail: BggGameDetail): GameEntity {
        val initials = InitialConsonantExtractor.extract(detail.name).takeIf { it != detail.name }
        return try {
            gameRepository.save(
                GameEntity(
                    bggId = detail.id,
                    source = GameSource.BGG,
                    nameEn = detail.name,
                    nameInitials = initials,
                    thumbnailUrl = detail.thumbnailUrl,
                    minPlayers = detail.minPlayers,
                    maxPlayers = detail.maxPlayers,
                    playtimeMinutes = detail.playtimeMinutes,
                ),
            )
        } catch (ex: DataIntegrityViolationException) {
            // A concurrent request already cached this bgg_id — recover instead of failing.
            gameRepository.findByBggId(detail.id) ?: throw ex
        }
    }
}
