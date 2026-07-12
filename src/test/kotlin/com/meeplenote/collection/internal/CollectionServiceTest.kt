package com.meeplenote.collection.internal

import com.meeplenote.common.api.BusinessException
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class CollectionServiceTest {

    private val collectionRepository = mock<CollectionRepository>()
    private val gameLookup = mock<GameLookup>()

    private val collectionService = CollectionService(collectionRepository, gameLookup)

    private val userId = 1L
    private val gameId = 10L

    @Test
    fun `존재하지 않는 게임에 upsert하면 GAME_NOT_FOUND를 던진다`() {
        whenever(gameLookup.getSummary(gameId))
            .thenThrow(BusinessException("GAME_NOT_FOUND", "존재하지 않는 게임입니다", HttpStatus.NOT_FOUND))

        assertThrows<BusinessException> {
            collectionService.upsert(userId, gameId, CollectionStatus.OWNED)
        }

        verify(collectionRepository, never()).save(any())
    }

    @Test
    fun `기존 항목이 없으면 새로 생성한다`() {
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(id = gameId, nameKo = "카탄", nameEn = "Catan"))
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(null)
        val saved = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.WISHED)
        whenever(collectionRepository.save(any())).thenReturn(saved)

        val response = collectionService.upsert(userId, gameId, CollectionStatus.WISHED)

        assertThat(response.gameId).isEqualTo(gameId)
        assertThat(response.status).isEqualTo(CollectionStatus.WISHED)
    }

    @Test
    fun `기존 항목이 있으면 상태만 갱신한다`() {
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(id = gameId, nameKo = "카탄", nameEn = "Catan"))
        val existing = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.WISHED)
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(existing)

        val response = collectionService.upsert(userId, gameId, CollectionStatus.OWNED)

        assertThat(response.status).isEqualTo(CollectionStatus.OWNED)
        verify(collectionRepository, never()).save(any())
    }

    @Test
    fun `제거 시 기존 항목이 있으면 삭제한다`() {
        val existing = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.OWNED)
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(existing)

        collectionService.remove(userId, gameId)

        verify(collectionRepository).delete(existing)
    }

    @Test
    fun `제거 시 기존 항목이 없으면 아무 것도 하지 않는다`() {
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(null)

        collectionService.remove(userId, gameId)

        verify(collectionRepository, never()).delete(any())
    }
}
