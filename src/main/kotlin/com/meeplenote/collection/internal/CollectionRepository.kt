package com.meeplenote.collection.internal

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

interface CollectionRepository : JpaRepository<CollectionEntity, Long> {
    fun findByUserIdAndGameId(userId: Long, gameId: Long): CollectionEntity?
    fun findAllByUserIdAndStatus(userId: Long, status: CollectionStatus, sort: Sort): List<CollectionEntity>
    fun findAllByUserId(userId: Long, sort: Sort): List<CollectionEntity>
    fun countByUserIdAndStatus(userId: Long, status: CollectionStatus): Long
}
