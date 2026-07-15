package com.meeplenote.play.internal

import com.meeplenote.collection.api.CollectionPlayTracker
import com.meeplenote.play.api.ImportPlayerInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class PlayBulkImporterServiceTest {

    private val playRepository = mock<PlayRepository>()
    private val playPlayerRepository = mock<PlayPlayerRepository>()
    private val playerRepository = mock<PlayerRepository>()
    private val playerNameResolver = PlayerNameResolver(playerRepository)
    private val collectionPlayTracker = mock<CollectionPlayTracker>()

    private val service = PlayBulkImporterService(
        playRepository,
        playPlayerRepository,
        playerRepository,
        playerNameResolver,
        collectionPlayTracker,
    )

    private val userId = 1L
    private val gameId = 10L
    private val playedAt = LocalDate.of(2026, 7, 1)

    @Test
    fun `동일 게임 날짜 플레이어 집합의 기록이 없으면 새로 생성한다`() {
        whenever(playRepository.findAllByUserIdAndGameIdAndPlayedAt(userId, gameId, playedAt)).thenReturn(emptyList())
        whenever(playerRepository.findAllByUserIdAndNameIn(eq(userId), any())).thenReturn(emptyList())
        whenever(playerRepository.saveAll(any<List<PlayerEntity>>())).thenAnswer { invocation ->
            (invocation.arguments[0] as List<PlayerEntity>)
        }
        val saved = PlayEntity(userId = userId, gameId = gameId, playedAt = playedAt)
        whenever(playRepository.saveAndFlush(any())).thenReturn(saved)

        val result = service.importPlay(userId, gameId, playedAt, listOf(ImportPlayerInput("철수", 10, true)))

        assertThat(result.created).isTrue()
        verify(collectionPlayTracker).recordPlay(userId, gameId, playedAt)
    }

    /** PlayEntity/PlayerEntity의 id는 JPA @GeneratedValue라 직접 생성 시 항상 0 — mock으로 값을 고정해 구분한다. */
    private fun playerWithId(playerId: Long, name: String): PlayerEntity {
        val player = mock<PlayerEntity>()
        whenever(player.id).thenReturn(playerId)
        whenever(player.name).thenReturn(name)
        return player
    }

    @Test
    fun `같은 게임 날짜 플레이어 집합의 기록이 이미 있으면 건너뛴다`() {
        val existing = mock<PlayEntity>()
        whenever(existing.id).thenReturn(1L)
        whenever(playRepository.findAllByUserIdAndGameIdAndPlayedAt(userId, gameId, playedAt)).thenReturn(listOf(existing))
        whenever(playPlayerRepository.findAllByPlayIdIn(listOf(1L)))
            .thenReturn(listOf(PlayPlayerEntity(playId = 1L, playerId = 100L, score = 10, isWinner = true)))
        val chulsoo = playerWithId(100L, "철수")
        whenever(playerRepository.findAllByUserIdAndIdIn(userId, listOf(100L))).thenReturn(listOf(chulsoo))

        val result = service.importPlay(userId, gameId, playedAt, listOf(ImportPlayerInput("철수", 10, true)))

        assertThat(result.created).isFalse()
        assertThat(result.playId).isEqualTo(1L)
        verify(playRepository, org.mockito.kotlin.never()).saveAndFlush(any())
    }

    @Test
    fun `같은 날짜여도 플레이어 집합이 다르면 새로 생성한다`() {
        val existing = mock<PlayEntity>()
        whenever(existing.id).thenReturn(1L)
        whenever(playRepository.findAllByUserIdAndGameIdAndPlayedAt(userId, gameId, playedAt)).thenReturn(listOf(existing))
        whenever(playPlayerRepository.findAllByPlayIdIn(listOf(1L)))
            .thenReturn(listOf(PlayPlayerEntity(playId = 1L, playerId = 100L, score = 10, isWinner = true)))
        val chulsoo = playerWithId(100L, "철수")
        whenever(playerRepository.findAllByUserIdAndIdIn(userId, listOf(100L))).thenReturn(listOf(chulsoo))
        whenever(playerRepository.findAllByUserIdAndNameIn(eq(userId), any())).thenReturn(emptyList())
        whenever(playerRepository.saveAll(any<List<PlayerEntity>>())).thenAnswer { invocation ->
            (invocation.arguments[0] as List<PlayerEntity>)
        }
        val saved = PlayEntity(userId = userId, gameId = gameId, playedAt = playedAt)
        whenever(playRepository.saveAndFlush(any())).thenReturn(saved)

        val result = service.importPlay(userId, gameId, playedAt, listOf(ImportPlayerInput("영희", 8, false)))

        assertThat(result.created).isTrue()
    }
}
