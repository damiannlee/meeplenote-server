package com.meeplenote.collection.internal

import com.meeplenote.game.api.GameLookup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class CollectionResponse(
    val gameId: Long,
    val status: CollectionStatus,
) {
    companion object {
        fun of(entity: CollectionEntity) = CollectionResponse(gameId = entity.gameId, status = entity.status)
    }
}

@Service
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val gameLookup: GameLookup,
) {

    @Transactional
    fun upsert(userId: Long, gameId: Long, status: CollectionStatus): CollectionResponse {
        gameLookup.getSummary(gameId)
        val existing = collectionRepository.findByUserIdAndGameId(userId, gameId)
        if (existing != null) {
            existing.status = status
            existing.updatedAt = Instant.now()
            return CollectionResponse.of(existing)
        }
        val created = collectionRepository.save(CollectionEntity(userId = userId, gameId = gameId, status = status))
        return CollectionResponse.of(created)
    }

    @Transactional
    fun remove(userId: Long, gameId: Long) {
        collectionRepository.findByUserIdAndGameId(userId, gameId)
            ?.let { collectionRepository.delete(it) }
    }
}
