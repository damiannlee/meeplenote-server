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
    fun getCollections(userId: Long, status: CollectionStatus?, sort: CollectionSort): CollectionListResponse {
        val entities = findEntities(userId, status, sort)
        val gamesById = gameLookup.getSummaries(entities.map { it.gameId }).associateBy { it.id }
        val items = buildItems(entities, gamesById, sort)
        val counts = CollectionCounts(
            owned = collectionRepository.countByUserIdAndStatus(userId, CollectionStatus.OWNED),
            wished = collectionRepository.countByUserIdAndStatus(userId, CollectionStatus.WISHED),
        )
        return CollectionListResponse(items = items, counts = counts)
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
