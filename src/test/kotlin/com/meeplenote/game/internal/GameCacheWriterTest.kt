package com.meeplenote.game.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException

class GameCacheWriterTest {

    private val gameRepository = mock<GameRepository>()
    private val gameCacheWriter = GameCacheWriter(gameRepository)

    @Test
    fun `빈 목록은 저장소를 호출하지 않는다`() {
        val result = gameCacheWriter.cacheAll(emptyList())

        assertThat(result).isEmpty()
        verify(gameRepository, never()).findAllByBggIdIn(any())
    }

    @Test
    fun `이미 캐시된 bggId는 다시 저장하지 않고 배치로 한 번만 조회한다`() {
        val existing = GameEntity(bggId = 13L, source = GameSource.BGG, nameEn = "CATAN")
        whenever(gameRepository.findAllByBggIdIn(listOf(13L, 42L))).thenReturn(listOf(existing))
        whenever(gameRepository.save(any())).thenAnswer { it.getArgument<GameEntity>(0) }

        val details = listOf(
            BggGameDetail(13L, "CATAN", null, 3, 4, 120),
            BggGameDetail(42L, "DOMINION", null, 2, 4, 30),
        )
        val result = gameCacheWriter.cacheAll(details)

        assertThat(result).hasSize(2)
        verify(gameRepository, times(1)).findAllByBggIdIn(any())
        verify(gameRepository, times(1)).save(any())
    }

    @Test
    fun `동시 삽입으로 유니크 제약이 깨지면 기존 행을 다시 조회해 반환한다`() {
        whenever(gameRepository.findAllByBggIdIn(listOf(13L))).thenReturn(emptyList())
        whenever(gameRepository.save(any())).thenThrow(DataIntegrityViolationException("duplicate"))
        val recovered = GameEntity(bggId = 13L, source = GameSource.BGG, nameEn = "CATAN")
        whenever(gameRepository.findByBggId(13L)).thenReturn(recovered)

        val result = gameCacheWriter.cacheAll(listOf(BggGameDetail(13L, "CATAN", null, 3, 4, 120)))

        assertThat(result).containsExactly(recovered)
    }
}
