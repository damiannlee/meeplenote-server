package com.meeplenote.play.internal

import com.meeplenote.play.api.GamePlayCount
import com.meeplenote.play.api.MonthlyPlayCount
import com.meeplenote.play.api.PlayStatsProvider
import com.meeplenote.play.api.PlayStatsSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

private const val TREND_MONTHS = 12
private const val TOP_GAMES_LIMIT = 5

@Service
class PlayStatsProviderService(
    private val playRepository: PlayRepository,
) : PlayStatsProvider {

    @Transactional(readOnly = true)
    override fun getSummary(userId: Long): PlayStatsSummary {
        val currentMonth = YearMonth.now()
        val monthStart = currentMonth.atDay(1)
        val trendStart = currentMonth.minusMonths((TREND_MONTHS - 1).toLong())

        return PlayStatsSummary(
            totalPlays = playRepository.countByUserId(userId),
            playsThisMonth = playRepository.countByUserIdAndPlayedAtGreaterThanEqual(userId, monthStart),
            monthlyTrend = buildMonthlyTrend(userId, trendStart),
            topGames = buildTopGames(userId),
        )
    }

    private fun buildMonthlyTrend(userId: Long, trendStart: YearMonth): List<MonthlyPlayCount> {
        val countsByMonth: Map<YearMonth, Long> = playRepository
            .countGroupedByMonth(userId, trendStart.atDay(1))
            .associate { YearMonth.parse(it.getMonth()) to it.getPlayCount() }
        return (0 until TREND_MONTHS).map { offset ->
            val month = trendStart.plusMonths(offset.toLong())
            MonthlyPlayCount(month = month, count = countsByMonth[month] ?: 0)
        }
    }

    private fun buildTopGames(userId: Long): List<GamePlayCount> =
        playRepository.findTopGamesByPlayCount(userId, TOP_GAMES_LIMIT)
            .map { GamePlayCount(gameId = it.getGameId(), count = it.getPlayCount()) }
}
