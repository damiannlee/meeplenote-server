package com.meeplenote.game.internal

import com.meeplenote.common.api.BusinessException
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameLookupService(
    private val gameRepository: GameRepository,
) : GameLookup {

    @Transactional(readOnly = true)
    override fun getSummary(gameId: Long): GameSummary {
        val entity = gameRepository.findById(gameId)
            .orElseThrow { BusinessException("GAME_NOT_FOUND", "존재하지 않는 게임입니다", HttpStatus.NOT_FOUND) }
        return toSummary(entity)
    }

    @Transactional(readOnly = true)
    override fun getSummaries(gameIds: Collection<Long>): List<GameSummary> {
        if (gameIds.isEmpty()) return emptyList()
        return gameRepository.findAllByIdIn(gameIds).map { toSummary(it) }
    }

    @Transactional(readOnly = true)
    override fun findByBggId(bggId: Long): GameSummary? =
        gameRepository.findByBggId(bggId)?.let { toSummary(it) }

    @Transactional(readOnly = true)
    override fun findCandidatesByName(name: String, limit: Int): List<GameSummary> =
        gameRepository.searchByName(name, limit).map { toSummary(it) }

    private fun toSummary(entity: GameEntity) =
        GameSummary(id = entity.id, nameKo = entity.nameKo, nameEn = entity.nameEn, thumbnailUrl = entity.thumbnailUrl)
}
