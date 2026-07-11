package com.meeplenote.game.internal

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class GameSummary(
    val id: Long,
    val bggId: Long?,
    val nameKo: String?,
    val nameEn: String?,
    val thumbnailUrl: String?,
    val minPlayers: Short?,
    val maxPlayers: Short?,
    val playtime: Short?,
    val source: String,
)

data class GameSearchResponse(
    val items: List<GameSummary>,
    val hasMore: Boolean,
) {
    companion object {
        fun of(items: List<GameSummary>, limit: Int) = GameSearchResponse(items = items, hasMore = items.size == limit)
    }
}

/**
 * Search priority: local DB hit (1+ rows) returns immediately without calling BGG.
 * Local miss (0 rows) tries BGG search+detail lookup within the latency budget
 * (bgg.timeout-ms) and merges the result; on timeout it returns an empty local
 * result and lets the in-flight fetch keep caching in the background (ADR-003).
 */
@Service
class GameSearchService(
    private val gameRepository: GameRepository,
    private val bggClient: BggClient,
    private val gameCacheWriter: GameCacheWriter,
    @Qualifier("gameCacheExecutor") private val gameCacheExecutor: Executor,
    @Value("\${bgg.timeout-ms}") private val bggTimeoutMs: Long,
) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int): GameSearchResponse {
        val localResults = searchLocal(query, limit)
        if (localResults.isNotEmpty()) {
            return GameSearchResponse.of(localResults.map { it.toSummary() }, limit)
        }

        val future = CompletableFuture.supplyAsync({ fetchAndCacheFromBgg(query, limit) }, gameCacheExecutor)
        val merged = try {
            future.get(bggTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (ex: TimeoutException) {
            emptyList()
        }
        return GameSearchResponse.of(merged.map { it.toSummary() }, limit)
    }

    private fun searchLocal(query: String, limit: Int): List<GameEntity> =
        if (InitialConsonantExtractor.isInitialsOnly(query)) {
            gameRepository.searchByInitials(query, limit)
        } else {
            gameRepository.searchByName(query, limit)
        }

    private fun fetchAndCacheFromBgg(query: String, limit: Int): List<GameEntity> {
        val candidates = bggClient.search(query).take(limit)
        if (candidates.isEmpty()) return emptyList()

        val details = bggClient.fetchThings(candidates.map { it.id })
        return gameCacheWriter.cacheAll(details)
    }

    private fun GameEntity.toSummary() = GameSummary(
        id = id,
        bggId = bggId,
        nameKo = nameKo,
        nameEn = nameEn,
        thumbnailUrl = thumbnailUrl,
        minPlayers = minPlayers,
        maxPlayers = maxPlayers,
        playtime = playtimeMinutes,
        source = source.name.lowercase(),
    )
}
