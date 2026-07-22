package com.meeplenote.play.internal

import org.springframework.data.jpa.repository.JpaRepository

interface PlayerGroupMemberRepository : JpaRepository<PlayerGroupMemberEntity, Long> {
    fun findAllByGroupIdIn(groupIds: Collection<Long>): List<PlayerGroupMemberEntity>
    fun findAllByPlayerIdIn(playerIds: Collection<Long>): List<PlayerGroupMemberEntity>
    fun findByGroupIdAndPlayerId(groupId: Long, playerId: Long): PlayerGroupMemberEntity?
    fun deleteByGroupIdAndPlayerId(groupId: Long, playerId: Long)
}
