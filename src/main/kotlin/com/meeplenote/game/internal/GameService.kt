package com.meeplenote.game.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CustomGameResponse(
    val id: Long,
    val source: String,
) {
    companion object {
        fun of(entity: GameEntity) = CustomGameResponse(id = entity.id, source = entity.source.name.lowercase())
    }
}

@Service
class GameService(
    private val gameRepository: GameRepository,
) {
    @Transactional
    fun registerCustom(userId: Long, name: String): CustomGameResponse {
        val entity = GameEntity(
            source = GameSource.CUSTOM,
            createdByUserId = userId,
            nameKo = name,
            nameInitials = InitialConsonantExtractor.extract(name).takeIf { it != name },
        )
        return CustomGameResponse.of(gameRepository.save(entity))
    }
}
