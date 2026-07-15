package com.meeplenote.dataimport.internal

import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BgStatsGameMatcherTest {

    private val gameLookup = mock<GameLookup>()
    private val matcher = BgStatsGameMatcher(gameLookup)

    @Test
    fun `bggId가 있고 로컬에 존재하면 그 게임으로 매칭한다`() {
        whenever(gameLookup.findByBggId(13)).thenReturn(GameSummary(id = 100, nameKo = null, nameEn = "Catan", thumbnailUrl = null))

        val result = matcher.match(listOf(BgStatsGame(id = 1, bggId = 13, name = "Catan")))

        assertThat(result.matchedGameIdByBgStatsGameId).containsEntry(1L, 100L)
        assertThat(result.unmatched).isEmpty()
    }

    @Test
    fun `bggId가 0이거나 로컬에 없으면 이름 정확 일치로 매칭한다`() {
        whenever(gameLookup.findByBggId(0)).thenReturn(null)
        whenever(gameLookup.findCandidatesByName("루미큐브", 3))
            .thenReturn(listOf(GameSummary(id = 200, nameKo = "루미큐브", nameEn = null, thumbnailUrl = null)))

        val result = matcher.match(listOf(BgStatsGame(id = 2, bggId = 0, name = "루미큐브")))

        assertThat(result.matchedGameIdByBgStatsGameId).containsEntry(2L, 200L)
    }

    @Test
    fun `이름이 정확히 일치하지 않으면 후보와 함께 unmatched로 분류한다`() {
        whenever(gameLookup.findByBggId(0)).thenReturn(null)
        whenever(gameLookup.findCandidatesByName("루미큐브 익스텐션", 3))
            .thenReturn(listOf(GameSummary(id = 200, nameKo = "루미큐브", nameEn = null, thumbnailUrl = null)))

        val result = matcher.match(listOf(BgStatsGame(id = 2, bggId = 0, name = "루미큐브 익스텐션")))

        assertThat(result.matchedGameIdByBgStatsGameId).isEmpty()
        assertThat(result.unmatched).hasSize(1)
        assertThat(result.unmatched[0].name).isEqualTo("루미큐브 익스텐션")
        assertThat(result.unmatched[0].candidates).containsExactly(GameCandidate(gameId = 200, name = "루미큐브"))
    }

    @Test
    fun `nameOverrides에 있으면 bggId 매칭보다 우선한다`() {
        val result = matcher.match(
            games = listOf(BgStatsGame(id = 3, bggId = null, name = "커스텀게임")),
            nameOverrides = mapOf("커스텀게임" to 999L),
        )

        assertThat(result.matchedGameIdByBgStatsGameId).containsEntry(3L, 999L)
    }
}
