package com.meeplenote.game.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameSearchServiceTest {

    private val gameRepository = mock<GameRepository>()
    private val bggClient = mock<BggClient>()
    private val gameCacheWriter = mock<GameCacheWriter>()
    private var executor: ExecutorService = Executors.newFixedThreadPool(2)

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }

    private fun service(timeoutMs: Long = 1500) =
        GameSearchService(gameRepository, bggClient, gameCacheWriter, executor, timeoutMs)

    @Test
    fun `로컬 결과가 있으면 BGG를 호출하지 않는다`() {
        val cached = GameEntity(source = GameSource.BGG, nameEn = "Catan")
        whenever(gameRepository.searchByName("catan", 10)).thenReturn(listOf(cached))

        val result = service().search("catan", 10)

        assertThat(result.items).hasSize(1)
        verify(bggClient, never()).search(any())
    }

    @Test
    fun `초성으로만 구성된 검색어는 초성 컬럼을 조회한다`() {
        whenever(gameRepository.searchByInitials("ㅋㅌ", 10)).thenReturn(emptyList())
        whenever(bggClient.search("ㅋㅌ")).thenReturn(emptyList())

        service().search("ㅋㅌ", 10)

        verify(gameRepository).searchByInitials("ㅋㅌ", 10)
    }

    @Test
    fun `캐시 미스 시 BGG 결과를 조회해 저장하고 병합 응답한다`() {
        whenever(gameRepository.searchByName("catan", 10)).thenReturn(emptyList())
        whenever(bggClient.search("catan")).thenReturn(listOf(BggSearchResult(13L, "CATAN")))
        val details = listOf(BggGameDetail(13L, "CATAN", "https://thumb.jpg", 3, 4, 120))
        whenever(bggClient.fetchThings(listOf(13L))).thenReturn(details)
        whenever(gameCacheWriter.cacheAll(details)).thenReturn(
            listOf(GameEntity(bggId = 13L, source = GameSource.BGG, nameEn = "CATAN")),
        )

        val result = service().search("catan", 10)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].nameEn).isEqualTo("CATAN")
        assertThat(result.items[0].source).isEqualTo("bgg")
    }

    @Test
    fun `BGG 응답이 지연 예산을 넘으면 빈 결과를 즉시 반환한다`() {
        whenever(gameRepository.searchByName("slow", 10)).thenReturn(emptyList())
        whenever(bggClient.search("slow")).thenAnswer {
            Thread.sleep(300)
            emptyList<BggSearchResult>()
        }

        val result = service(timeoutMs = 50).search("slow", 10)

        assertThat(result.items).isEmpty()
    }
}
