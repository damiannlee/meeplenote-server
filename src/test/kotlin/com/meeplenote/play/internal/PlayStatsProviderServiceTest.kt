package com.meeplenote.play.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

class PlayStatsProviderServiceTest {

    private val playRepository = mock<PlayRepository>()
    private val service = PlayStatsProviderService(playRepository)

    private val userId = 1L

    private fun monthlyRow(month: String, count: Long) = mock<MonthlyPlayCountRow> {
        on { getMonth() } doReturn month
        on { getPlayCount() } doReturn count
    }

    private fun gameRow(gameId: Long, count: Long) = mock<GamePlayCountRow> {
        on { getGameId() } doReturn gameId
        on { getPlayCount() } doReturn count
    }

    @Test
    fun `기록이 없는 유저는 모든 값이 0이고 12개월이 채워진다`() {
        whenever(playRepository.countByUserId(userId)).thenReturn(0)
        whenever(playRepository.countByUserIdAndPlayedAtGreaterThanEqual(any(), any())).thenReturn(0)
        whenever(playRepository.countGroupedByMonth(any(), any())).thenReturn(emptyList())
        whenever(playRepository.findTopGamesByPlayCount(any(), any())).thenReturn(emptyList())

        val summary = service.getSummary(userId)

        assertThat(summary.totalPlays).isEqualTo(0)
        assertThat(summary.playsThisMonth).isEqualTo(0)
        assertThat(summary.monthlyTrend).hasSize(12)
        assertThat(summary.monthlyTrend).allMatch { it.count == 0L }
        assertThat(summary.monthlyTrend.last().month).isEqualTo(YearMonth.now())
        assertThat(summary.monthlyTrend.first().month).isEqualTo(YearMonth.now().minusMonths(11))
        assertThat(summary.topGames).isEmpty()
    }

    @Test
    fun `쿼리 결과가 있는 달은 채워지고 없는 달은 0으로 채워진다`() {
        val currentMonth = YearMonth.now()
        val currentMonthRow = monthlyRow(currentMonth.toString(), 3)
        val twoMonthsAgoRow = monthlyRow(currentMonth.minusMonths(2).toString(), 4)
        whenever(playRepository.countByUserId(userId)).thenReturn(7)
        whenever(playRepository.countByUserIdAndPlayedAtGreaterThanEqual(any(), any())).thenReturn(3)
        whenever(playRepository.countGroupedByMonth(any(), any())).thenReturn(listOf(currentMonthRow, twoMonthsAgoRow))
        whenever(playRepository.findTopGamesByPlayCount(any(), any())).thenReturn(emptyList())

        val summary = service.getSummary(userId)

        val byMonth = summary.monthlyTrend.associate { it.month to it.count }
        assertThat(byMonth[currentMonth]).isEqualTo(3)
        assertThat(byMonth[currentMonth.minusMonths(2)]).isEqualTo(4)
        assertThat(byMonth[currentMonth.minusMonths(1)]).isEqualTo(0)
    }

    @Test
    fun `top5 게임을 순서대로 매핑한다`() {
        val topRow = gameRow(gameId = 100L, count = 5)
        val secondRow = gameRow(gameId = 200L, count = 3)
        whenever(playRepository.countByUserId(userId)).thenReturn(10)
        whenever(playRepository.countByUserIdAndPlayedAtGreaterThanEqual(any(), any())).thenReturn(1)
        whenever(playRepository.countGroupedByMonth(any(), any())).thenReturn(emptyList())
        whenever(playRepository.findTopGamesByPlayCount(any(), any())).thenReturn(listOf(topRow, secondRow))

        val summary = service.getSummary(userId)

        assertThat(summary.topGames).extracting("gameId", "count")
            .containsExactly(Tuple.tuple(100L, 5L), Tuple.tuple(200L, 3L))
    }

    @Test
    fun `이번 달 시작일을 기준으로 이번 달 플레이 수를 조회한다`() {
        whenever(playRepository.countByUserId(userId)).thenReturn(1)
        whenever(playRepository.countByUserIdAndPlayedAtGreaterThanEqual(any(), any())).thenReturn(1)
        whenever(playRepository.countGroupedByMonth(any(), any())).thenReturn(emptyList())
        whenever(playRepository.findTopGamesByPlayCount(any(), any())).thenReturn(emptyList())

        service.getSummary(userId)

        verify(playRepository).countByUserIdAndPlayedAtGreaterThanEqual(userId, LocalDate.now().withDayOfMonth(1))
    }
}
