package com.meeplenote.collection.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class CollectionLookupServiceTest {

    private val collectionRepository = mock<CollectionRepository>()
    private val service = CollectionLookupService(collectionRepository)

    private val userId = 1L
    private val gameId = 10L

    @Test
    fun `컬렉션에 없는 게임은 isOwned가 false다`() {
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(null)

        assertThat(service.isOwned(userId, gameId)).isFalse()
    }

    @Test
    fun `WISHED 상태는 isOwned가 false다`() {
        val entity = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.WISHED)
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(entity)

        assertThat(service.isOwned(userId, gameId)).isFalse()
    }

    @Test
    fun `OWNED 게임에 기록하면 playCount와 lastPlayedAt이 갱신된다`() {
        val entity = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.OWNED)
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(entity)
        val playedAt = LocalDate.of(2026, 7, 1)

        service.recordPlay(userId, gameId, playedAt)

        assertThat(entity.playCount).isEqualTo(1)
        assertThat(entity.lastPlayedAt).isEqualTo(playedAt)
    }

    @Test
    fun `WISHED 게임도 기록하면 playCount와 lastPlayedAt이 갱신된다`() {
        val entity = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.WISHED)
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(entity)
        val playedAt = LocalDate.of(2026, 7, 1)

        service.recordPlay(userId, gameId, playedAt)

        assertThat(entity.playCount).isEqualTo(1)
        assertThat(entity.lastPlayedAt).isEqualTo(playedAt)
    }

    @Test
    fun `컬렉션에 없는 게임에 기록해도 예외가 발생하지 않는다`() {
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(null)

        service.recordPlay(userId, gameId, LocalDate.now())
    }

    @Test
    fun `더 이른 날짜로 재호출해도 lastPlayedAt은 최신 날짜를 유지한다`() {
        val entity = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.OWNED)
        whenever(collectionRepository.findByUserIdAndGameId(userId, gameId)).thenReturn(entity)
        service.recordPlay(userId, gameId, LocalDate.of(2026, 7, 10))

        service.recordPlay(userId, gameId, LocalDate.of(2026, 7, 1))

        assertThat(entity.playCount).isEqualTo(2)
        assertThat(entity.lastPlayedAt).isEqualTo(LocalDate.of(2026, 7, 10))
    }

    @Test
    fun `countNoPlay는 OWNED이면서 playCount가 0인 게임 수를 조회한다`() {
        whenever(collectionRepository.countByUserIdAndStatusAndPlayCount(userId, CollectionStatus.OWNED, 0))
            .thenReturn(3L)

        assertThat(service.countNoPlay(userId)).isEqualTo(3L)
    }

    @Test
    fun `getAllForUser는 컬렉션 엔티티를 export 레코드로 변환한다`() {
        val entity = CollectionEntity(userId = userId, gameId = gameId, status = CollectionStatus.OWNED)
        entity.playCount = 5
        entity.lastPlayedAt = LocalDate.of(2026, 7, 1)
        whenever(collectionRepository.findAllByUserId(eq(userId), any())).thenReturn(listOf(entity))

        val records = service.getAllForUser(userId)

        assertThat(records).hasSize(1)
        assertThat(records[0].gameId).isEqualTo(gameId)
        assertThat(records[0].status).isEqualTo("OWNED")
        assertThat(records[0].playCount).isEqualTo(5)
        assertThat(records[0].lastPlayedAt).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(records[0].addedAt).isEqualTo(entity.createdAt)
    }

    @Test
    fun `컬렉션이 없으면 getAllForUser는 빈 목록을 반환한다`() {
        whenever(collectionRepository.findAllByUserId(eq(userId), any())).thenReturn(emptyList())

        assertThat(service.getAllForUser(userId)).isEmpty()
    }
}
