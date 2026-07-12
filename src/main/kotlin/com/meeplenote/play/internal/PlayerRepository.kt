package com.meeplenote.play.internal

import org.springframework.data.jpa.repository.JpaRepository

interface PlayerRepository : JpaRepository<PlayerEntity, Long> {
    fun findAllByUserIdAndIdIn(userId: Long, ids: Collection<Long>): List<PlayerEntity>
    fun findAllByUserIdAndNameIn(userId: Long, names: Collection<String>): List<PlayerEntity>
}
