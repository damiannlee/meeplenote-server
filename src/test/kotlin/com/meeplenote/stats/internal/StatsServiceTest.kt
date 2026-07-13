package com.meeplenote.stats.internal

import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import com.meeplenote.play.api.GamePlayCount
import com.meeplenote.play.api.MonthlyPlayCount
import com.meeplenote.play.api.PlayStatsProvider
import com.meeplenote.play.api.PlayStatsSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.YearMonth

class StatsServiceTest {

    private val playStatsProvider = mock<PlayStatsProvider>()
    private val collectionLookup = mock<CollectionLookup>()
    private val gameLookup = mock<GameLookup>()
    private val service = StatsService(playStatsProvider, collectionLookup, gameLookup)

    private val userId = 1L

    @Test
    fun `기록이 없으면 topGames가 비어있고 gameLookup을 호출하지 않는다`() {
        whenever(playStatsProvider.getSummary(userId)).thenReturn(
            PlayStatsSummary(totalPlays = 0, playsThisMonth = 0, monthlyTrend = emptyList(), topGames = emptyList()),
        )
        whenever(collectionLookup.countNoPlay(userId)).thenReturn(0)

        val response = service.getSummary(userId)

        assertThat(response.topGames).isEmpty()
        verify(gameLookup, never()).getSummaries(any())
    }

    @Test
    fun `top 게임에 게임명을 배치로 붙여서 반환한다`() {
        val month = YearMonth.of(2026, 7)
        whenever(playStatsProvider.getSummary(userId)).thenReturn(
            PlayStatsSummary(
                totalPlays = 5,
                playsThisMonth = 2,
                monthlyTrend = listOf(MonthlyPlayCount(month, 2)),
                topGames = listOf(GamePlayCount(gameId = 100L, count = 5), GamePlayCount(gameId = 200L, count = 3)),
            ),
        )
        whenever(gameLookup.getSummaries(listOf(100L, 200L))).thenReturn(
            listOf(
                GameSummary(id = 100L, nameKo = "카탄", nameEn = "Catan", thumbnailUrl = null),
                GameSummary(id = 200L, nameKo = null, nameEn = "Terraforming Mars", thumbnailUrl = null),
            ),
        )
        whenever(collectionLookup.countNoPlay(userId)).thenReturn(1)

        val response = service.getSummary(userId)

        assertThat(response.totalPlays).isEqualTo(5)
        assertThat(response.playsThisMonth).isEqualTo(2)
        assertThat(response.monthlyTrend).containsExactly(MonthlyTrendItem(month = "2026-07", count = 2))
        assertThat(response.topGames).containsExactly(
            TopGameItem(gameId = 100L, nameKo = "카탄", nameEn = "Catan", count = 5),
            TopGameItem(gameId = 200L, nameKo = null, nameEn = "Terraforming Mars", count = 3),
        )
        assertThat(response.noPlayCount).isEqualTo(1)
        verify(gameLookup).getSummaries(listOf(100L, 200L))
    }

    @Test
    fun `gameLookup에 없는 게임은 이름이 null로 채워진다`() {
        whenever(playStatsProvider.getSummary(userId)).thenReturn(
            PlayStatsSummary(
                totalPlays = 1,
                playsThisMonth = 1,
                monthlyTrend = emptyList(),
                topGames = listOf(GamePlayCount(gameId = 999L, count = 1)),
            ),
        )
        whenever(gameLookup.getSummaries(listOf(999L))).thenReturn(emptyList())
        whenever(collectionLookup.countNoPlay(userId)).thenReturn(0)

        val response = service.getSummary(userId)

        assertThat(response.topGames).containsExactly(TopGameItem(gameId = 999L, nameKo = null, nameEn = null, count = 1))
    }
}
