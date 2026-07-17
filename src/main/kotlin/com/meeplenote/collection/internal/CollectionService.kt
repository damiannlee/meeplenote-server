package com.meeplenote.collection.internal

import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

data class CollectionResponse(
    val gameId: Long,
    val status: CollectionStatus,
) {
    companion object {
        fun of(entity: CollectionEntity) = CollectionResponse(gameId = entity.gameId, status = entity.status)
    }
}

enum class CollectionSort {
    NAME,
    RECENT_PLAY,
    PLAY_COUNT,
}

data class CollectionItemResponse(
    val gameId: Long,
    val nameKo: String?,
    val thumbnailUrl: String?,
    val status: CollectionStatus,
    val playCount: Int,
    val lastPlayedAt: LocalDate?,
    val isNoPlay: Boolean,
    val minPlayers: Short?,
    val maxPlayers: Short?,
    val playtime: Short?,
)

data class CollectionCounts(val owned: Long, val wished: Long)

data class CollectionListResponse(val items: List<CollectionItemResponse>, val counts: CollectionCounts)

@Service
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val gameLookup: GameLookup,
) {

    @Transactional
    fun upsert(userId: Long, gameId: Long, status: CollectionStatus): CollectionResponse {
        gameLookup.getSummary(gameId)
        val existing = collectionRepository.findByUserIdAndGameId(userId, gameId)
        if (existing != null) {
            existing.status = status
            existing.updatedAt = Instant.now()
            return CollectionResponse.of(existing)
        }
        val created = collectionRepository.save(CollectionEntity(userId = userId, gameId = gameId, status = status))
        return CollectionResponse.of(created)
    }

    @Transactional
    fun remove(userId: Long, gameId: Long) {
        collectionRepository.findByUserIdAndGameId(userId, gameId)
            ?.let { collectionRepository.delete(it) }
    }

    @Transactional(readOnly = true)
    fun getCollections(
        userId: Long,
        status: CollectionStatus?,
        sort: CollectionSort,
        players: Int? = null,
        maxPlaytime: Int? = null,
    ): CollectionListResponse {
        val entities = findEntities(userId, status, sort)
        val gamesById = gameLookup.getSummaries(entities.map { it.gameId }).associateBy { it.id }
        val items = buildItems(entities, gamesById, sort)
            .filter { matchesFilters(it, players, maxPlaytime) }
        val counts = CollectionCounts(
            owned = collectionRepository.countByUserIdAndStatus(userId, CollectionStatus.OWNED),
            wished = collectionRepository.countByUserIdAndStatus(userId, CollectionStatus.WISHED),
        )
        return CollectionListResponse(items = items, counts = counts)
    }

    private fun matchesFilters(item: CollectionItemResponse, players: Int?, maxPlaytime: Int?): Boolean {
        if (players != null) {
            val min = item.minPlayers ?: return false
            val max = item.maxPlayers ?: return false
            if (players < min || players > max) return false
        }
        if (maxPlaytime != null) {
            val playtime = item.playtime ?: return false
            if (playtime > maxPlaytime) return false
        }
        return true
    }

    private fun findEntities(userId: Long, status: CollectionStatus?, sort: CollectionSort): List<CollectionEntity> {
        val jpaSort = toJpaSort(sort)
        return if (status != null) {
            collectionRepository.findAllByUserIdAndStatus(userId, status, jpaSort)
        } else {
            collectionRepository.findAllByUserId(userId, jpaSort)
        }
    }

    private fun toJpaSort(sort: CollectionSort): Sort =
        when (sort) {
            CollectionSort.RECENT_PLAY -> Sort.by(Sort.Order.desc("lastPlayedAt").nullsLast())
            CollectionSort.PLAY_COUNT -> Sort.by(Sort.Order.desc("playCount"))
            CollectionSort.NAME -> Sort.unsorted()
        }

    private fun buildItems(
        entities: List<CollectionEntity>,
        gamesById: Map<Long, GameSummary>,
        sort: CollectionSort,
    ): List<CollectionItemResponse> {
        val items = entities.map { entity ->
            val game = gamesById[entity.gameId]
            CollectionItemResponse(
                gameId = entity.gameId,
                nameKo = game?.nameKo,
                thumbnailUrl = game?.thumbnailUrl,
                status = entity.status,
                playCount = entity.playCount,
                lastPlayedAt = entity.lastPlayedAt,
                isNoPlay = entity.status == CollectionStatus.OWNED && entity.playCount == 0,
                minPlayers = game?.minPlayers,
                maxPlayers = game?.maxPlayers,
                playtime = game?.playtime,
            )
        }
        return if (sort == CollectionSort.NAME) {
            val namesByGameId = gamesById.mapValues { (_, game) -> game.nameKo ?: game.nameEn ?: "" }
            items.sortedBy { namesByGameId[it.gameId] ?: "" }
        } else {
            items
        }
    }
}
