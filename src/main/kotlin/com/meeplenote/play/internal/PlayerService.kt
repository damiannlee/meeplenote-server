package com.meeplenote.play.internal

import com.meeplenote.common.api.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PlayerGroupRef(val id: Long, val name: String)

data class PlayerSummaryResponse(
    val id: Long,
    val name: String,
    val isFavorite: Boolean,
    val groups: List<PlayerGroupRef>,
) {
    companion object {
        fun of(entity: PlayerEntity, groups: List<PlayerGroupRef>) =
            PlayerSummaryResponse(entity.id, entity.name, entity.isFavorite, groups)
    }
}

data class PlayerGroupMemberRef(val id: Long, val name: String)

data class PlayerGroupResponse(
    val id: Long,
    val name: String,
    val players: List<PlayerGroupMemberRef>,
) {
    companion object {
        fun of(entity: PlayerGroupEntity, players: List<PlayerGroupMemberRef>) =
            PlayerGroupResponse(entity.id, entity.name, players)
    }
}

@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val playerGroupRepository: PlayerGroupRepository,
    private val playerGroupMemberRepository: PlayerGroupMemberRepository,
) {

    @Transactional(readOnly = true)
    fun listPlayers(userId: Long): List<PlayerSummaryResponse> =
        buildPlayerSummaries(playerRepository.findAllByUserIdOrderByIsFavoriteDescNameAsc(userId))

    @Transactional(readOnly = true)
    fun listRecentlyPlayedWith(userId: Long, limit: Int): List<PlayerSummaryResponse> =
        buildPlayerSummaries(playerRepository.findRecentlyPlayedWith(userId, limit))

    @Transactional
    fun setFavorite(userId: Long, playerId: Long, isFavorite: Boolean): PlayerSummaryResponse {
        val player = findOwnedPlayer(userId, playerId)
        player.isFavorite = isFavorite
        return buildPlayerSummaries(listOf(player)).single()
    }

    private fun buildPlayerSummaries(players: List<PlayerEntity>): List<PlayerSummaryResponse> {
        if (players.isEmpty()) return emptyList()
        val memberships = playerGroupMemberRepository.findAllByPlayerIdIn(players.map { it.id })
        val groupsById = playerGroupRepository.findAllById(memberships.map { it.groupId }.distinct()).associateBy { it.id }
        val groupRefsByPlayerId = memberships
            .groupBy({ it.playerId }) { PlayerGroupRef(groupsById.getValue(it.groupId).id, groupsById.getValue(it.groupId).name) }
        return players.map { PlayerSummaryResponse.of(it, groupRefsByPlayerId[it.id] ?: emptyList()) }
    }

    private fun findOwnedPlayer(userId: Long, playerId: Long): PlayerEntity =
        playerRepository.findByUserIdAndId(userId, playerId)
            ?: throw BusinessException("PLAYER_NOT_FOUND", "존재하지 않거나 소유하지 않은 플레이어입니다", HttpStatus.NOT_FOUND)

    @Transactional
    fun createGroup(userId: Long, name: String): PlayerGroupResponse {
        if (playerGroupRepository.existsByUserIdAndName(userId, name)) {
            throw duplicateGroupNameException()
        }
        val group = try {
            playerGroupRepository.saveAndFlush(PlayerGroupEntity(userId = userId, name = name))
        } catch (ex: DataIntegrityViolationException) {
            throw duplicateGroupNameException()
        }
        return buildGroupResponses(listOf(group)).single()
    }

    private fun duplicateGroupNameException() =
        BusinessException("PLAYER_GROUP_NAME_DUPLICATE", "이미 존재하는 그룹 이름입니다", HttpStatus.CONFLICT)

    @Transactional(readOnly = true)
    fun listGroups(userId: Long): List<PlayerGroupResponse> =
        buildGroupResponses(playerGroupRepository.findAllByUserId(userId))

    private fun buildGroupResponses(groups: List<PlayerGroupEntity>): List<PlayerGroupResponse> {
        if (groups.isEmpty()) return emptyList()
        val memberships = playerGroupMemberRepository.findAllByGroupIdIn(groups.map { it.id })
        val playersById = playerRepository.findAllById(memberships.map { it.playerId }.distinct()).associateBy { it.id }
        val memberRefsByGroupId = memberships
            .groupBy({ it.groupId }) { PlayerGroupMemberRef(playersById.getValue(it.playerId).id, playersById.getValue(it.playerId).name) }
        return groups.map { PlayerGroupResponse.of(it, memberRefsByGroupId[it.id] ?: emptyList()) }
    }

    @Transactional
    fun addPlayerToGroup(userId: Long, groupId: Long, playerId: Long): PlayerGroupResponse {
        val group = findOwnedGroup(userId, groupId)
        val player = findOwnedPlayer(userId, playerId)
        if (playerGroupMemberRepository.findByGroupIdAndPlayerId(group.id, player.id) == null) {
            playerGroupMemberRepository.save(PlayerGroupMemberEntity(groupId = group.id, playerId = player.id))
        }
        return buildGroupResponses(listOf(group)).single()
    }

    @Transactional
    fun removePlayerFromGroup(userId: Long, groupId: Long, playerId: Long) {
        val group = findOwnedGroup(userId, groupId)
        playerGroupMemberRepository.deleteByGroupIdAndPlayerId(group.id, playerId)
    }

    @Transactional
    fun deleteGroup(userId: Long, groupId: Long) {
        playerGroupRepository.delete(findOwnedGroup(userId, groupId))
    }

    private fun findOwnedGroup(userId: Long, groupId: Long): PlayerGroupEntity =
        playerGroupRepository.findByUserIdAndId(userId, groupId)
            ?: throw BusinessException("PLAYER_GROUP_NOT_FOUND", "존재하지 않거나 소유하지 않은 그룹입니다", HttpStatus.NOT_FOUND)
}
