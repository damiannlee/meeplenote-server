package com.meeplenote.play.internal

import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.collection.api.CollectionPlayTracker
import com.meeplenote.common.api.BusinessException
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate
import java.util.UUID

class PlayServiceTest {

    private val playRepository = mock<PlayRepository>()
    private val playerRepository = mock<PlayerRepository>()
    private val playPlayerRepository = mock<PlayPlayerRepository>()
    private val gameLookup = mock<GameLookup>()
    private val collectionLookup = mock<CollectionLookup>()
    private val collectionPlayTracker = mock<CollectionPlayTracker>()

    private val playService = PlayService(
        playRepository,
        playerRepository,
        playPlayerRepository,
        gameLookup,
        collectionLookup,
        collectionPlayTracker,
    )

    private val userId = 1L
    private val gameId = 10L
    private val idempotencyKey: UUID = UUID.randomUUID()

    @Test
    fun `게임이 존재하지 않으면 GAME_NOT_FOUND를 던진다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId))
            .thenThrow(BusinessException("GAME_NOT_FOUND", "존재하지 않는 게임입니다", org.springframework.http.HttpStatus.NOT_FOUND))

        assertThrows<BusinessException> {
            playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))
        }

        verify(playRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `동일 idempotency key로 재호출하면 기존 기록을 그대로 반환한다`() {
        val existing = PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(existing)
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(3)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)

        val response = playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        assertThat(response.totalPlayCountForGame).isEqualTo(3)
        verify(playRepository, never()).saveAndFlush(any())
        verify(gameLookup, never()).getSummary(any())
        verify(collectionPlayTracker, never()).recordPlay(any(), any(), any())
    }

    @Test
    fun `동시 요청으로 유니크 제약 위반이 발생하면 기존 기록을 재조회해 반환하고 컬렉션 통계는 갱신하지 않는다`() {
        val existing = PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
            .thenReturn(null)
            .thenReturn(existing)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenThrow(DataIntegrityViolationException("duplicate"))
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)

        val response = playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        assertThat(response.gameId).isEqualTo(gameId)
        verify(collectionPlayTracker, never()).recordPlay(any(), any(), any())
    }

    @Test
    fun `신규 기록 삽입에 성공하면 컬렉션 플레이 통계를 갱신한다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        val playedAt = LocalDate.now()
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = playedAt, idempotencyKey = idempotencyKey)
        }
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(true)

        playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        verify(collectionPlayTracker).recordPlay(userId, gameId, playedAt)
    }

    @Test
    fun `유니크 제약 위반 후 재조회해도 못 찾으면 예외를 다시 던진다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenThrow(DataIntegrityViolationException("duplicate"))

        assertThrows<DataIntegrityViolationException> {
            playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))
        }
    }

    @Test
    fun `playedAt 생략 시 오늘 날짜로 기록된다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        val captor = argumentCaptor<PlayEntity>()
        whenever(playRepository.saveAndFlush(captor.capture())).thenAnswer { captor.firstValue }
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)

        val response = playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        assertThat(response.playedAt).isEqualTo(LocalDate.now())
    }

    @Test
    fun `미래 playedAt이면 FUTURE_PLAYED_AT 422를 던진다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        val request = CreatePlayRequest(gameId = gameId, playedAt = LocalDate.now().plusDays(1))

        val ex = assertThrows<BusinessException> { playService.recordPlay(userId, idempotencyKey, request) }

        assertThat(ex.code).isEqualTo("FUTURE_PLAYED_AT")
    }

    @Test
    fun `타 유저 소유 playerId를 지정하면 PLAYER_NOT_FOUND를 던진다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        }
        whenever(playerRepository.findAllByUserIdAndIdIn(eq(userId), any())).thenReturn(emptyList())
        val request = CreatePlayRequest(gameId = gameId, players = listOf(PlayerInput(playerId = 99L)))

        val ex = assertThrows<BusinessException> { playService.recordPlay(userId, idempotencyKey, request) }

        assertThat(ex.code).isEqualTo("PLAYER_NOT_FOUND")
    }

    @Test
    fun `name만 준 신규 플레이어는 saveAll 1회로 생성된다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        }
        whenever(playerRepository.findAllByUserIdAndNameIn(eq(userId), any())).thenReturn(emptyList())
        whenever(playerRepository.saveAll(any<List<PlayerEntity>>())).thenAnswer { invocation ->
            (invocation.getArgument<List<PlayerEntity>>(0))
        }
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)
        val request = CreatePlayRequest(
            gameId = gameId,
            players = listOf(PlayerInput(name = "철수"), PlayerInput(name = "영희")),
        )

        playService.recordPlay(userId, idempotencyKey, request)

        verify(playerRepository, org.mockito.kotlin.times(1)).saveAll(any<List<PlayerEntity>>())
        verify(playPlayerRepository, org.mockito.kotlin.times(1)).saveAll(any<List<PlayPlayerEntity>>())
    }

    @Test
    fun `기존 이름의 플레이어는 재사용하고 신규 생성하지 않는다`() {
        val existingPlayer = PlayerEntity(userId = userId, name = "철수")
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        }
        whenever(playerRepository.findAllByUserIdAndNameIn(eq(userId), any())).thenReturn(listOf(existingPlayer))
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)
        val request = CreatePlayRequest(gameId = gameId, players = listOf(PlayerInput(name = "철수")))

        playService.recordPlay(userId, idempotencyKey, request)

        verify(playerRepository, never()).saveAll(any<List<PlayerEntity>>())
    }

    @Test
    fun `OWNED 컬렉션이 없으면 suggestAddToCollection이 true다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        }
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)

        val response = playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        assertThat(response.suggestAddToCollection).isTrue()
    }

    @Test
    fun `OWNED 컬렉션이 있으면 suggestAddToCollection이 false다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        }
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(1)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(true)

        val response = playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        assertThat(response.suggestAddToCollection).isFalse()
    }

    @Test
    fun `삽입 이후 totalPlayCountForGame을 반환한다`() {
        whenever(playRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).thenReturn(null)
        whenever(gameLookup.getSummary(gameId)).thenReturn(GameSummary(gameId, "카탄", null, null))
        whenever(playRepository.saveAndFlush(any())).thenAnswer {
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now(), idempotencyKey = idempotencyKey)
        }
        whenever(playRepository.countByUserIdAndGameId(userId, gameId)).thenReturn(5)
        whenever(collectionLookup.isOwned(userId, gameId)).thenReturn(false)

        val response = playService.recordPlay(userId, idempotencyKey, CreatePlayRequest(gameId = gameId))

        assertThat(response.totalPlayCountForGame).isEqualTo(5)
        verify(playRepository).countByUserIdAndGameId(userId, gameId)
    }
}
