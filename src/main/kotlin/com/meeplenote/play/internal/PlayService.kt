package com.meeplenote.play.internal

import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.collection.api.CollectionPlayTracker
import com.meeplenote.common.api.BusinessException
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth
import java.util.Base64
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

data class PlayListItemResponse(
    val id: Long,
    val gameId: Long,
    val gameName: String,
    val thumbnailUrl: String?,
    val playedAt: LocalDate,
) {
    companion object {
        fun of(entity: PlayEntity, game: GameSummary?) =
            PlayListItemResponse(
                id = entity.id,
                gameId = entity.gameId,
                gameName = game?.nameKo ?: game?.nameEn ?: "",
                thumbnailUrl = game?.thumbnailUrl,
                playedAt = entity.playedAt,
            )
    }
}

data class PlayListResponse(
    val items: List<PlayListItemResponse>,
    val nextCursor: String?,
)

data class PlayCalendarResponse(
    val items: List<PlayListItemResponse>,
)

@Service
class PlayService(
    private val playRepository: PlayRepository,
    private val playerRepository: PlayerRepository,
    private val playPlayerRepository: PlayPlayerRepository,
    private val gameLookup: GameLookup,
    private val collectionLookup: CollectionLookup,
    private val collectionPlayTracker: CollectionPlayTracker,
    private val playerNameResolver: PlayerNameResolver,
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
            return buildResponse(userId, playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey) ?: throw ex)
        }

        collectionPlayTracker.recordPlay(userId, play.gameId, play.playedAt)
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
        val resolvedByName = playerNameResolver.resolveOrCreateByName(userId, namesToResolve)

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

    private fun buildResponse(userId: Long, play: PlayEntity): PlayResponse {
        val totalPlayCount = playRepository.countByUserIdAndGameId(userId, play.gameId)
        val suggestAddToCollection = !collectionLookup.isOwned(userId, play.gameId)
        return PlayResponse.of(play, totalPlayCount, suggestAddToCollection)
    }

    @Transactional(readOnly = true)
    fun listPlays(userId: Long, cursor: String?, limit: Int): PlayListResponse {
        val rows = fetchPage(userId, cursor, limit)
        val page = rows.take(limit)
        val gamesById = gameLookup.getSummaries(page.map { it.gameId }.distinct()).associateBy { it.id }
        val items = page.map { PlayListItemResponse.of(it, gamesById[it.gameId]) }
        val nextCursor = if (rows.size > limit) encodeCursor(page.last()) else null
        return PlayListResponse(items, nextCursor)
    }

    @Transactional(readOnly = true)
    fun listPlaysByMonth(userId: Long, yearMonth: YearMonth): PlayCalendarResponse {
        val from = yearMonth.atDay(1)
        val to = yearMonth.atEndOfMonth()
        val plays = playRepository.findAllByUserIdAndPlayedAtBetweenOrderByPlayedAtAscIdAsc(userId, from, to)
        val gamesById = gameLookup.getSummaries(plays.map { it.gameId }.distinct()).associateBy { it.id }
        val items = plays.map { PlayListItemResponse.of(it, gamesById[it.gameId]) }
        return PlayCalendarResponse(items)
    }

    private fun fetchPage(userId: Long, cursor: String?, limit: Int): List<PlayEntity> {
        if (cursor == null) return playRepository.findFirstPageByUserId(userId, limit + 1)
        val (cursorPlayedAt, cursorId) = decodeCursor(cursor)
        return playRepository.findNextPageByUserId(userId, cursorPlayedAt, cursorId, limit + 1)
    }

    private fun encodeCursor(entity: PlayEntity): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${entity.playedAt}_${entity.id}".toByteArray())

    private fun decodeCursor(cursor: String): Pair<LocalDate, Long> {
        val decoded = runCatching { String(Base64.getUrlDecoder().decode(cursor)) }
            .getOrElse { throw invalidCursorException() }
        val (playedAt, id) = decoded.split("_").takeIf { it.size == 2 } ?: throw invalidCursorException()
        return runCatching { LocalDate.parse(playedAt) to id.toLong() }.getOrElse { throw invalidCursorException() }
    }

    private fun invalidCursorException() =
        BusinessException("INVALID_CURSOR", "유효하지 않은 커서입니다", HttpStatus.BAD_REQUEST)
}
