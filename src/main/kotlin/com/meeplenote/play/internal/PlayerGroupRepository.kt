package com.meeplenote.play.internal

import org.springframework.data.jpa.repository.JpaRepository

interface PlayerGroupRepository : JpaRepository<PlayerGroupEntity, Long> {
    fun findAllByUserId(userId: Long): List<PlayerGroupEntity>
    fun findByUserIdAndId(userId: Long, id: Long): PlayerGroupEntity?
    fun existsByUserIdAndName(userId: Long, name: String): Boolean
}
