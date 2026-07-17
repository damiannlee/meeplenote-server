package com.meeplenote.play.api

import java.time.LocalDate

data class PlayExportPlayer(
    val name: String,
    val score: Int?,
    val isWinner: Boolean,
)

data class PlayExportRecord(
    val id: Long,
    val gameId: Long,
    val playedAt: LocalDate,
    val note: String?,
    val rating: Short?,
    val players: List<PlayExportPlayer>,
)

interface PlayExportProvider {
    fun getAllForUser(userId: Long): List<PlayExportRecord>
}
