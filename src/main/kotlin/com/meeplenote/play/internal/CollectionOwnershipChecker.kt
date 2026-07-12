package com.meeplenote.play.internal

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component

/**
 * TODO(M3): replace with collection.api.CollectionLookup once the collection module exists.
 * The `collection` module has no code yet, so this reads the `collections` table directly
 * without crossing any module boundary ArchUnit protects.
 */
@Component
class CollectionOwnershipChecker(
    @PersistenceContext private val entityManager: EntityManager,
) {
    fun isOwned(userId: Long, gameId: Long): Boolean {
        val result = entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM collections WHERE user_id = :userId AND game_id = :gameId AND status = 'OWNED')",
        )
            .setParameter("userId", userId)
            .setParameter("gameId", gameId)
            .singleResult
        return result as Boolean
    }
}
