package com.meeplenote.play.internal

import com.meeplenote.common.api.BusinessException
import com.meeplenote.game.api.GameLookup
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class PlayResponse(
    val id: Long,
    val gameId: Long,
    val playedAt: LocalDate,
    val totalPlayCountForGame: Int,
    val suggestAddToCollection: Boolean,
) {
    companion object {
        fun of(entity: PlayEntity, totalPlayCountForGame: Int, suggestAddToCollection: Boolean) =
            PlayResponse(
                id = entity.id,
                gameId = entity.gameId,
                playedAt = entity.playedAt,
                totalPlayCountForGame = totalPlayCountForGame,
                suggestAddToCollection = suggestAddToCollection,
            )
    }
}

@Service
class PlayService(
    private val playRepository: PlayRepository,
    private val playerRepository: PlayerRepository,
    private val playPlayerRepository: PlayPlayerRepository,
    private val gameLookup: GameLookup,
    private val collectionOwnershipChecker: CollectionOwnershipChecker,
) {

    @Transactional
    fun recordPlay(userId: Long, idempotencyKey: UUID, request: CreatePlayRequest): PlayResponse {
        playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            ?.let { return buildResponse(userId, it) }

        gameLookup.getSummary(request.gameId)
        val playedAt = resolvePlayedAt(request.playedAt)

        val play = try {
            insertPlay(userId, idempotencyKey, request, playedAt)
        } catch (ex: DataIntegrityViolationException) {
            playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey) ?: throw ex
        }

        return buildResponse(userId, play)
    }

    private fun resolvePlayedAt(playedAt: LocalDate?): LocalDate {
        val resolved = playedAt ?: LocalDate.now()
        if (resolved.isAfter(LocalDate.now())) {
            throw BusinessException("FUTURE_PLAYED_AT", "플레이 날짜는 미래일 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY)
        }
        return resolved
    }

    private fun insertPlay(userId: Long, idempotencyKey: UUID, request: CreatePlayRequest, playedAt: LocalDate): PlayEntity {
        val play = playRepository.saveAndFlush(
            PlayEntity(
                userId = userId,
                gameId = request.gameId,
                playedAt = playedAt,
                note = request.note,
                rating = request.rating?.toShort(),
                photoKey = request.photoKey,
                idempotencyKey = idempotencyKey,
            ),
        )
        attachPlayers(userId, play.id, request.players)
        return play
    }

    private fun attachPlayers(userId: Long, playId: Long, players: List<PlayerInput>) {
        if (players.isEmpty()) return
        val resolvedPlayerIds = resolvePlayerIds(userId, players)
        val entities = players.mapIndexed { index, input ->
            PlayPlayerEntity(
                playId = playId,
                playerId = resolvedPlayerIds[index],
                score = input.score,
                isWinner = input.isWinner,
            )
        }
        playPlayerRepository.saveAll(entities)
    }

    private fun resolvePlayerIds(userId: Long, players: List<PlayerInput>): List<Long> {
        val explicitIds = players.mapNotNull { it.playerId }
        val ownedById = playerRepository.findAllByUserIdAndIdIn(userId, explicitIds).associateBy { it.id }
        validateOwnership(explicitIds, ownedById)

        val namesToResolve = players.filter { it.playerId == null }.mapNotNull { it.name }
        val resolvedByName = resolveOrCreateByName(userId, namesToResolve)

        return players.map { input ->
            input.playerId?.let { ownedById.getValue(it).id } ?: resolvedByName.getValue(requireName(input)).id
        }
    }

    private fun requireName(input: PlayerInput): String =
        input.name ?: throw BusinessException("PLAYER_INPUT_INVALID", "playerId 또는 name이 필요합니다", HttpStatus.UNPROCESSABLE_ENTITY)

    private fun validateOwnership(explicitIds: List<Long>, ownedById: Map<Long, PlayerEntity>) {
        val missing = explicitIds.filterNot { ownedById.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw BusinessException("PLAYER_NOT_FOUND", "존재하지 않거나 소유하지 않은 플레이어입니다", HttpStatus.NOT_FOUND)
        }
    }

    private fun resolveOrCreateByName(userId: Long, names: List<String>): Map<String, PlayerEntity> {
        if (names.isEmpty()) return emptyMap()
        val distinctNames = names.distinct()
        val existing = playerRepository.findAllByUserIdAndNameIn(userId, distinctNames).associateBy { it.name }
        val missingNames = distinctNames.filterNot { existing.containsKey(it) }
        val created = if (missingNames.isEmpty()) {
            emptyList()
        } else {
            playerRepository.saveAll(missingNames.map { PlayerEntity(userId = userId, name = it) })
        }
        return existing + created.associateBy { it.name }
    }

    private fun buildResponse(userId: Long, play: PlayEntity): PlayResponse {
        val totalPlayCount = playRepository.countByUserIdAndGameId(userId, play.gameId)
        val suggestAddToCollection = !collectionOwnershipChecker.isOwned(userId, play.gameId)
        return PlayResponse.of(play, totalPlayCount, suggestAddToCollection)
    }
}
