package com.meeplenote.play.api

import java.time.LocalDate

data class ImportPlayerInput(
    val name: String,
    val score: Int?,
    val isWinner: Boolean,
)

data class ImportPlayResult(
    val created: Boolean,
    val playId: Long,
)

interface PlayBulkImporter {
    fun importPlay(userId: Long, gameId: Long, playedAt: LocalDate, players: List<ImportPlayerInput>): ImportPlayResult
}
