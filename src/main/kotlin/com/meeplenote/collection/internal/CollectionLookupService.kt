package com.meeplenote.collection.internal

import com.meeplenote.collection.api.CollectionLookup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionLookupService(
    private val collectionRepository: CollectionRepository,
) : CollectionLookup {

    @Transactional(readOnly = true)
    override fun isOwned(userId: Long, gameId: Long): Boolean =
        collectionRepository.findByUserIdAndGameId(userId, gameId)?.status == CollectionStatus.OWNED
}
