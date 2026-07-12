package com.meeplenote.collection.internal

import org.springframework.data.jpa.repository.JpaRepository

interface CollectionRepository : JpaRepository<CollectionEntity, Long> {
    fun findByUserIdAndGameId(userId: Long, gameId: Long): CollectionEntity?
}
