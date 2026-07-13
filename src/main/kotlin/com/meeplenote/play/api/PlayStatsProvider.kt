package com.meeplenote.play.api

import java.time.YearMonth

data class MonthlyPlayCount(val month: YearMonth, val count: Long)

data class GamePlayCount(val gameId: Long, val count: Long)

data class PlayStatsSummary(
    val totalPlays: Long,
    val playsThisMonth: Long,
    val monthlyTrend: List<MonthlyPlayCount>,
    val topGames: List<GamePlayCount>,
)

interface PlayStatsProvider {
    fun getSummary(userId: Long): PlayStatsSummary
}
