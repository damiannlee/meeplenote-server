package com.meeplenote.play.internal

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component

/**
 * TODO(M4): replace with collection.api.CollectionLookup (now implemented by
 * collection.internal.CollectionLookupService) once M4 wires play<->collection together.
 * This still reads the `collections` table directly to avoid changing PlayService's
 * dependency wiring outside this story's scope.
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
