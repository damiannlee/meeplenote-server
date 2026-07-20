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
    private val playerNameResolver = PlayerNameResolver(playerRepository)

    private val playService = PlayService(
        playRepository,
        playerRepository,
        playPlayerRepository,
        gameLookup,
        collectionLookup,
        collectionPlayTracker,
        playerNameResolver,
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

    @Test
    fun `커서 없이 조회하면 첫 페이지 쿼리를 쓰고 limit 이하 결과면 nextCursor가 없다`() {
        val plays = listOf(PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now()))
        whenever(playRepository.findFirstPageByUserId(userId, 21)).thenReturn(plays)
        whenever(gameLookup.getSummaries(listOf(gameId))).thenReturn(listOf(GameSummary(gameId, "카탄", null, null)))

        val response = playService.listPlays(userId, cursor = null, limit = 20)

        assertThat(response.items).hasSize(1)
        assertThat(response.nextCursor).isNull()
        verify(playRepository, never()).findNextPageByUserId(any(), any(), any(), any())
    }

    @Test
    fun `결과가 limit을 초과하면 items는 limit개만 반환하고 nextCursor를 채운다`() {
        val plays = (1..3).map { PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now().minusDays(it.toLong())) }
        whenever(playRepository.findFirstPageByUserId(userId, 3)).thenReturn(plays)
        whenever(gameLookup.getSummaries(listOf(gameId))).thenReturn(listOf(GameSummary(gameId, "카탄", null, null)))

        val response = playService.listPlays(userId, cursor = null, limit = 2)

        assertThat(response.items).hasSize(2)
        assertThat(response.nextCursor).isNotNull()
    }

    @Test
    fun `커서가 있으면 다음 페이지 쿼리에 디코딩된 날짜와 id를 넘긴다`() {
        val cursorPlayedAt = LocalDate.now().minusDays(3)
        val cursorId = 42L
        val cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("${cursorPlayedAt}_$cursorId".toByteArray())
        whenever(playRepository.findNextPageByUserId(userId, cursorPlayedAt, cursorId, 21)).thenReturn(emptyList())

        playService.listPlays(userId, cursor = cursor, limit = 20)

        verify(playRepository).findNextPageByUserId(userId, cursorPlayedAt, cursorId, 21)
        verify(playRepository, never()).findFirstPageByUserId(any(), any())
    }

    @Test
    fun `잘못된 형식의 커서는 INVALID_CURSOR를 던진다`() {
        val ex = assertThrows<BusinessException> { playService.listPlays(userId, cursor = "not-a-valid-cursor", limit = 20) }

        assertThat(ex.code).isEqualTo("INVALID_CURSOR")
    }

    @Test
    fun `같은 게임의 플레이가 여러 건이어도 게임 조회는 distinct id로 한 번만 배치 호출한다`() {
        val plays = listOf(
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now()),
            PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now().minusDays(1)),
        )
        whenever(playRepository.findFirstPageByUserId(userId, 21)).thenReturn(plays)
        whenever(gameLookup.getSummaries(listOf(gameId))).thenReturn(listOf(GameSummary(gameId, "카탄", null, null)))

        playService.listPlays(userId, cursor = null, limit = 20)

        verify(gameLookup, org.mockito.kotlin.times(1)).getSummaries(listOf(gameId))
    }

    @Test
    fun `게임명은 nameKo가 없으면 nameEn을 쓴다`() {
        val plays = listOf(PlayEntity(userId = userId, gameId = gameId, playedAt = LocalDate.now()))
        whenever(playRepository.findFirstPageByUserId(userId, 21)).thenReturn(plays)
        whenever(gameLookup.getSummaries(listOf(gameId))).thenReturn(listOf(GameSummary(gameId, null, "Catan", null)))

        val response = playService.listPlays(userId, cursor = null, limit = 20)

        assertThat(response.items.single().gameName).isEqualTo("Catan")
    }
}
