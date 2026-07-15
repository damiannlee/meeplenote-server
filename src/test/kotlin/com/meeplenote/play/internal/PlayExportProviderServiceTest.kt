package com.meeplenote.play.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class PlayExportProviderServiceTest {

    private val playRepository = mock<PlayRepository>()
    private val playPlayerRepository = mock<PlayPlayerRepository>()
    private val playerRepository = mock<PlayerRepository>()
    private val service = PlayExportProviderService(playRepository, playPlayerRepository, playerRepository)

    private val userId = 1L

    private fun playEntity(id: Long, gameId: Long, playedAt: LocalDate, note: String? = null): PlayEntity {
        val play = mock<PlayEntity>()
        whenever(play.id).thenReturn(id)
        whenever(play.gameId).thenReturn(gameId)
        whenever(play.playedAt).thenReturn(playedAt)
        whenever(play.note).thenReturn(note)
        whenever(play.rating).thenReturn(null)
        return play
    }

    private fun playerEntity(id: Long, name: String): PlayerEntity {
        val player = mock<PlayerEntity>()
        whenever(player.id).thenReturn(id)
        whenever(player.name).thenReturn(name)
        return player
    }

    @Test
    fun `플레이가 없으면 빈 목록을 반환하고 배치 조회를 하지 않는다`() {
        whenever(playRepository.findAllByUserId(any(), any())).thenReturn(emptyList())

        val records = service.getAllForUser(userId)

        assertThat(records).isEmpty()
        verify(playPlayerRepository, never()).findAllByPlayIdIn(any())
    }

    @Test
    fun `여러 플레이의 플레이어를 배치로 조회해 각 기록에 붙인다`() {
        val play1 = playEntity(id = 1L, gameId = 10L, playedAt = LocalDate.of(2026, 7, 1), note = "재밌었음")
        val play2 = playEntity(id = 2L, gameId = 20L, playedAt = LocalDate.of(2026, 7, 2))
        whenever(playRepository.findAllByUserId(any(), any())).thenReturn(listOf(play1, play2))

        val pp1 = PlayPlayerEntity(playId = 1L, playerId = 100L, score = 30, isWinner = true)
        val pp2 = PlayPlayerEntity(playId = 2L, playerId = 200L, score = null, isWinner = false)
        whenever(playPlayerRepository.findAllByPlayIdIn(listOf(1L, 2L))).thenReturn(listOf(pp1, pp2))

        val player1 = playerEntity(id = 100L, name = "민석")
        val player2 = playerEntity(id = 200L, name = "서연")
        whenever(playerRepository.findAllByUserIdAndIdIn(userId, listOf(100L, 200L))).thenReturn(listOf(player1, player2))

        val records = service.getAllForUser(userId)

        assertThat(records).hasSize(2)
        assertThat(records[0].gameId).isEqualTo(10L)
        assertThat(records[0].note).isEqualTo("재밌었음")
        assertThat(records[0].players).hasSize(1)
        assertThat(records[0].players[0].name).isEqualTo("민석")
        assertThat(records[0].players[0].score).isEqualTo(30)
        assertThat(records[0].players[0].isWinner).isTrue()

        assertThat(records[1].gameId).isEqualTo(20L)
        assertThat(records[1].players[0].name).isEqualTo("서연")
        assertThat(records[1].players[0].isWinner).isFalse()

        verify(playPlayerRepository).findAllByPlayIdIn(listOf(1L, 2L))
        verify(playerRepository).findAllByUserIdAndIdIn(userId, listOf(100L, 200L))
    }

    @Test
    fun `플레이어가 없는 기록은 빈 players 목록을 반환한다`() {
        val play = playEntity(id = 1L, gameId = 10L, playedAt = LocalDate.of(2026, 7, 1))
        whenever(playRepository.findAllByUserId(any(), any())).thenReturn(listOf(play))
        whenever(playPlayerRepository.findAllByPlayIdIn(listOf(1L))).thenReturn(emptyList())
        whenever(playerRepository.findAllByUserIdAndIdIn(userId, emptyList())).thenReturn(emptyList())

        val records = service.getAllForUser(userId)

        assertThat(records[0].players).isEmpty()
    }
}
