package com.meeplenote.collection.internal

import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.collection.api.CollectionPlayTracker
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class CollectionLookupService(
    private val collectionRepository: CollectionRepository,
) : CollectionLookup, CollectionPlayTracker {

    @Transactional(readOnly = true)
    override fun isOwned(userId: Long, gameId: Long): Boolean =
        collectionRepository.findByUserIdAndGameId(userId, gameId)?.status == CollectionStatus.OWNED

    @Transactional
    override fun recordPlay(userId: Long, gameId: Long, playedAt: LocalDate) {
        val collection = collectionRepository.findByUserIdAndGameId(userId, gameId) ?: return
        collection.playCount += 1
        collection.lastPlayedAt = maxOf(collection.lastPlayedAt ?: playedAt, playedAt)
        collection.updatedAt = Instant.now()
    }
}
