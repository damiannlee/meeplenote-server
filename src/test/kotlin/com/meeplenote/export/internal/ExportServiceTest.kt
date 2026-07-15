package com.meeplenote.export.internal

import com.meeplenote.collection.api.CollectionExportRecord
import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import com.meeplenote.play.api.PlayExportPlayer
import com.meeplenote.play.api.PlayExportProvider
import com.meeplenote.play.api.PlayExportRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class ExportServiceTest {

    private val playExportProvider = mock<PlayExportProvider>()
    private val collectionLookup = mock<CollectionLookup>()
    private val gameLookup = mock<GameLookup>()
    private val service = ExportService(playExportProvider, collectionLookup, gameLookup)

    private val userId = 1L

    @Test
    fun `플레이와 컬렉션이 없으면 빈 배열로 응답하고 gameLookup을 빈 목록으로 호출한다`() {
        whenever(playExportProvider.getAllForUser(userId)).thenReturn(emptyList())
        whenever(collectionLookup.getAllForUser(userId)).thenReturn(emptyList())
        whenever(gameLookup.getSummaries(emptyList())).thenReturn(emptyList())

        val response = service.exportAll(userId)

        assertThat(response.plays).isEmpty()
        assertThat(response.collections).isEmpty()
    }

    @Test
    fun `plays와 collections의 gameId를 합쳐 게임명을 한 번에 배치 조회한다`() {
        val playRecord = PlayExportRecord(
            id = 1L,
            gameId = 10L,
            playedAt = LocalDate.of(2026, 7, 1),
            note = "재밌었음",
            rating = 5,
            players = listOf(PlayExportPlayer(name = "민석", score = 30, isWinner = true)),
        )
        val collectionRecord = CollectionExportRecord(
            gameId = 20L,
            status = "OWNED",
            playCount = 3,
            lastPlayedAt = LocalDate.of(2026, 7, 2),
            addedAt = Instant.parse("2026-06-01T00:00:00Z"),
        )
        whenever(playExportProvider.getAllForUser(userId)).thenReturn(listOf(playRecord))
        whenever(collectionLookup.getAllForUser(userId)).thenReturn(listOf(collectionRecord))
        whenever(gameLookup.getSummaries(any())).thenReturn(
            listOf(
                GameSummary(id = 10L, nameKo = "카탄", nameEn = "Catan", thumbnailUrl = null),
                GameSummary(id = 20L, nameKo = null, nameEn = "Terraforming Mars", thumbnailUrl = null),
            ),
        )

        val response = service.exportAll(userId)

        assertThat(response.plays).hasSize(1)
        assertThat(response.plays[0].gameName).isEqualTo("카탄")
        assertThat(response.plays[0].players).hasSize(1)
        assertThat(response.plays[0].players[0].name).isEqualTo("민석")

        assertThat(response.collections).hasSize(1)
        assertThat(response.collections[0].gameName).isEqualTo("Terraforming Mars")
        assertThat(response.collections[0].status).isEqualTo("OWNED")

        verify(gameLookup).getSummaries(listOf(10L, 20L))
    }

    @Test
    fun `gameLookup에 없는 게임은 이름이 빈 문자열로 채워진다`() {
        val playRecord = PlayExportRecord(
            id = 1L,
            gameId = 999L,
            playedAt = LocalDate.of(2026, 7, 1),
            note = null,
            rating = null,
            players = emptyList(),
        )
        whenever(playExportProvider.getAllForUser(userId)).thenReturn(listOf(playRecord))
        whenever(collectionLookup.getAllForUser(userId)).thenReturn(emptyList())
        whenever(gameLookup.getSummaries(listOf(999L))).thenReturn(emptyList())

        val response = service.exportAll(userId)

        assertThat(response.plays[0].gameName).isEqualTo("")
        verify(gameLookup, never()).getSummaries(emptyList())
    }
}
