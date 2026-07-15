package com.meeplenote.play.internal

import com.meeplenote.collection.api.CollectionPlayTracker
import com.meeplenote.play.api.ImportPlayResult
import com.meeplenote.play.api.ImportPlayerInput
import com.meeplenote.play.api.PlayBulkImporter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PlayBulkImporterService(
    private val playRepository: PlayRepository,
    private val playPlayerRepository: PlayPlayerRepository,
    private val playerRepository: PlayerRepository,
    private val playerNameResolver: PlayerNameResolver,
    private val collectionPlayTracker: CollectionPlayTracker,
) : PlayBulkImporter {

    @Transactional
    override fun importPlay(userId: Long, gameId: Long, playedAt: LocalDate, players: List<ImportPlayerInput>): ImportPlayResult {
        val duplicate = findDuplicate(userId, gameId, playedAt, players.map { it.name }.toSet())
        if (duplicate != null) return ImportPlayResult(created = false, playId = duplicate)

        val play = playRepository.saveAndFlush(PlayEntity(userId = userId, gameId = gameId, playedAt = playedAt))
        attachPlayers(userId, play.id, players)
        collectionPlayTracker.recordPlay(userId, gameId, playedAt)
        return ImportPlayResult(created = true, playId = play.id)
    }

    private fun findDuplicate(userId: Long, gameId: Long, playedAt: LocalDate, incomingNames: Set<String>): Long? {
        val candidates = playRepository.findAllByUserIdAndGameIdAndPlayedAt(userId, gameId, playedAt)
        if (candidates.isEmpty()) return null

        val candidateIds = candidates.map { it.id }
        val links = playPlayerRepository.findAllByPlayIdIn(candidateIds)
        val linksByPlayId = links.groupBy { it.playId }
        val nameByPlayerId = playerRepository.findAllByUserIdAndIdIn(userId, links.map { it.playerId }.distinct())
            .associate { it.id to it.name }

        return candidates.firstOrNull { candidate ->
            val names = linksByPlayId[candidate.id].orEmpty().mapNotNull { nameByPlayerId[it.playerId] }.toSet()
            names == incomingNames
        }?.id
    }

    private fun attachPlayers(userId: Long, playId: Long, players: List<ImportPlayerInput>) {
        if (players.isEmpty()) return
        val resolvedByName = playerNameResolver.resolveOrCreateByName(userId, players.map { it.name })
        val entities = players.map { input ->
            PlayPlayerEntity(
                playId = playId,
                playerId = resolvedByName.getValue(input.name).id,
                score = input.score,
                isWinner = input.isWinner,
            )
        }
        playPlayerRepository.saveAll(entities)
    }
}
