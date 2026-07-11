package com.meeplenote.game.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GameSearchServiceTest {

    private val gameRepository = mock<GameRepository>()
    private val service = GameSearchService(gameRepository)

    @Test
    fun `이름 검색 결과를 반환한다`() {
        val cached = GameEntity(source = GameSource.BGG, nameEn = "Catan")
        whenever(gameRepository.searchByName("catan", 10)).thenReturn(listOf(cached))

        val result = service.search("catan", 10)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].nameEn).isEqualTo("Catan")
        assertThat(result.hasMore).isFalse()
    }

    @Test
    fun `초성으로만 구성된 검색어는 초성 컬럼을 조회한다`() {
        whenever(gameRepository.searchByInitials("ㅋㅌ", 10)).thenReturn(emptyList())

        service.search("ㅋㅌ", 10)

        verify(gameRepository).searchByInitials("ㅋㅌ", 10)
    }

    @Test
    fun `로컬에 일치하는 게임이 없으면 빈 결과를 반환한다`() {
        whenever(gameRepository.searchByName("nonexistent", 10)).thenReturn(emptyList())

        val result = service.search("nonexistent", 10)

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `결과 수가 limit과 같으면 hasMore가 true다`() {
        val cached = GameEntity(source = GameSource.CUSTOM, createdByUserId = 1L, nameKo = "테스트게임")
        whenever(gameRepository.searchByName("test", 1)).thenReturn(listOf(cached))

        val result = service.search("test", 1)

        assertThat(result.hasMore).isTrue()
    }
}
