package com.meeplenote.play.internal

import com.meeplenote.play.api.PlayExportPlayer
import com.meeplenote.play.api.PlayExportProvider
import com.meeplenote.play.api.PlayExportRecord
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlayExportProviderService(
    private val playRepository: PlayRepository,
    private val playPlayerRepository: PlayPlayerRepository,
    private val playerRepository: PlayerRepository,
) : PlayExportProvider {

    @Transactional(readOnly = true)
    override fun getAllForUser(userId: Long): List<PlayExportRecord> {
        val plays = playRepository.findAllByUserId(userId, Sort.by(Sort.Direction.ASC, "playedAt"))
        if (plays.isEmpty()) return emptyList()

        val playIds = plays.map { it.id }
        val playPlayersByPlayId = playPlayerRepository.findAllByPlayIdIn(playIds).groupBy { it.playId }

        val playerIds = playPlayersByPlayId.values.flatten().map { it.playerId }
        val playersById = playerRepository.findAllByUserIdAndIdIn(userId, playerIds).associateBy { it.id }

        return plays.map { play -> buildRecord(play, playPlayersByPlayId[play.id].orEmpty(), playersById) }
    }

    private fun buildRecord(
        play: PlayEntity,
        playPlayers: List<PlayPlayerEntity>,
        playersById: Map<Long, PlayerEntity>,
    ): PlayExportRecord {
        val players = playPlayers.map { pp ->
            PlayExportPlayer(
                name = playersById.getValue(pp.playerId).name,
                score = pp.score,
                isWinner = pp.isWinner,
            )
        }
        return PlayExportRecord(
            id = play.id,
            gameId = play.gameId,
            playedAt = play.playedAt,
            note = play.note,
            rating = play.rating,
            players = players,
        )
    }
}
