package com.meeplenote.export.internal

import com.meeplenote.collection.api.CollectionExportRecord
import com.meeplenote.collection.api.CollectionLookup
import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import com.meeplenote.play.api.PlayExportProvider
import com.meeplenote.play.api.PlayExportRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

data class ExportPlayerItem(val name: String, val score: Int?, val isWinner: Boolean)

data class ExportPlayItem(
    val id: Long,
    val gameId: Long,
    val gameName: String,
    val playedAt: LocalDate,
    val note: String?,
    val rating: Short?,
    val players: List<ExportPlayerItem>,
)

data class ExportCollectionItem(
    val gameId: Long,
    val gameName: String,
    val status: String,
    val playCount: Int,
    val lastPlayedAt: LocalDate?,
    val addedAt: Instant,
)

data class ExportResponse(
    val exportedAt: Instant,
    val plays: List<ExportPlayItem>,
    val collections: List<ExportCollectionItem>,
)

@Service
class ExportService(
    private val playExportProvider: PlayExportProvider,
    private val collectionLookup: CollectionLookup,
    private val gameLookup: GameLookup,
) {

    @Transactional(readOnly = true)
    fun exportAll(userId: Long): ExportResponse {
        val plays = playExportProvider.getAllForUser(userId)
        val collections = collectionLookup.getAllForUser(userId)

        val gameIds = plays.map { it.gameId } + collections.map { it.gameId }
        val gamesById = gameLookup.getSummaries(gameIds.distinct()).associateBy { it.id }

        return ExportResponse(
            exportedAt = Instant.now(),
            plays = plays.map { buildPlayItem(it, gamesById) },
            collections = collections.map { buildCollectionItem(it, gamesById) },
        )
    }

    private fun buildPlayItem(record: PlayExportRecord, gamesById: Map<Long, GameSummary>): ExportPlayItem =
        ExportPlayItem(
            id = record.id,
            gameId = record.gameId,
            gameName = resolveGameName(gamesById[record.gameId]),
            playedAt = record.playedAt,
            note = record.note,
            rating = record.rating,
            players = record.players.map { ExportPlayerItem(name = it.name, score = it.score, isWinner = it.isWinner) },
        )

    private fun buildCollectionItem(record: CollectionExportRecord, gamesById: Map<Long, GameSummary>): ExportCollectionItem =
        ExportCollectionItem(
            gameId = record.gameId,
            gameName = resolveGameName(gamesById[record.gameId]),
            status = record.status,
            playCount = record.playCount,
            lastPlayedAt = record.lastPlayedAt,
            addedAt = record.addedAt,
        )

    private fun resolveGameName(game: GameSummary?): String = game?.nameKo ?: game?.nameEn ?: ""
}
