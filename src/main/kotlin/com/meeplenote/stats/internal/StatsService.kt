package com.meeplenote.stats.internal

import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.game.api.GameLookup
import com.meeplenote.play.api.GamePlayCount
import com.meeplenote.play.api.PlayStatsProvider
import com.meeplenote.play.api.PlayStatsSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MonthlyTrendItem(val month: String, val count: Long)

data class TopGameItem(val gameId: Long, val nameKo: String?, val nameEn: String?, val count: Long)

data class StatsSummaryResponse(
    val totalPlays: Long,
    val playsThisMonth: Long,
    val monthlyTrend: List<MonthlyTrendItem>,
    val topGames: List<TopGameItem>,
    val noPlayCount: Long,
) {
    companion object {
        fun of(summary: PlayStatsSummary, topGames: List<TopGameItem>, noPlayCount: Long) =
            StatsSummaryResponse(
                totalPlays = summary.totalPlays,
                playsThisMonth = summary.playsThisMonth,
                monthlyTrend = summary.monthlyTrend.map { MonthlyTrendItem(month = it.month.toString(), count = it.count) },
                topGames = topGames,
                noPlayCount = noPlayCount,
            )
    }
}

@Service
class StatsService(
    private val playStatsProvider: PlayStatsProvider,
    private val collectionLookup: CollectionLookup,
    private val gameLookup: GameLookup,
) {

    @Transactional(readOnly = true)
    fun getSummary(userId: Long): StatsSummaryResponse {
        val summary = playStatsProvider.getSummary(userId)
        val topGames = buildTopGames(summary.topGames)
        val noPlayCount = collectionLookup.countNoPlay(userId)
        return StatsSummaryResponse.of(summary, topGames, noPlayCount)
    }

    private fun buildTopGames(topGames: List<GamePlayCount>): List<TopGameItem> {
        if (topGames.isEmpty()) return emptyList()
        val gamesById = gameLookup.getSummaries(topGames.map { it.gameId }).associateBy { it.id }
        return topGames.map { entry ->
            val game = gamesById[entry.gameId]
            TopGameItem(gameId = entry.gameId, nameKo = game?.nameKo, nameEn = game?.nameEn, count = entry.count)
        }
    }
}
