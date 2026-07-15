package com.meeplenote.collection.internal

import com.meeplenote.collection.api.CollectionExportRecord
import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.collection.api.CollectionPlayTracker
import org.springframework.data.domain.Sort
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

    @Transactional(readOnly = true)
    override fun countNoPlay(userId: Long): Long =
        collectionRepository.countByUserIdAndStatusAndPlayCount(userId, CollectionStatus.OWNED, 0)

    @Transactional(readOnly = true)
    override fun getAllForUser(userId: Long): List<CollectionExportRecord> =
        collectionRepository.findAllByUserId(userId, Sort.by(Sort.Direction.ASC, "createdAt")).map {
            CollectionExportRecord(
                gameId = it.gameId,
                status = it.status.name,
                playCount = it.playCount,
                lastPlayedAt = it.lastPlayedAt,
                addedAt = it.createdAt,
            )
        }

    /**
     * Tracks play stats regardless of OWNED/WISHED status — a wishlist game can be
     * played (e.g. at a friend's place) before the user ever buys it. The "no-play"
     * badge itself (isNoPlay) still only applies to OWNED rows; see CollectionService.
     */
    @Transactional
    override fun recordPlay(userId: Long, gameId: Long, playedAt: LocalDate) {
        val collection = collectionRepository.findByUserIdAndGameId(userId, gameId) ?: return
        collection.playCount += 1
        collection.lastPlayedAt = maxOf(collection.lastPlayedAt ?: playedAt, playedAt)
        collection.updatedAt = Instant.now()
    }
}
