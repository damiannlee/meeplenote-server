package com.meeplenote.dataimport.internal

import com.meeplenote.play.api.ImportPlayerInput
import com.meeplenote.play.api.PlayBulkImporter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class BgStatsImportProcessor(
    private val importJobRepository: ImportJobRepository,
    private val bgStatsFileParser: BgStatsFileParser,
    private val gameMatcher: BgStatsGameMatcher,
    private val playBulkImporter: PlayBulkImporter,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("importTaskExecutor")
    fun process(jobId: Long) {
        val job = importJobRepository.findById(jobId).orElse(null) ?: return
        try {
            runImport(job)
        } catch (ex: Exception) {
            log.error("Import job {} failed", jobId, ex)
            markFailed(job, ex)
        }
    }

    // No @Transactional here: each save()/importPlay() call below commits on its own,
    // so RUNNING is visible to GET polls immediately instead of only after the whole job finishes.
    fun runImport(job: ImportJobEntity) {
        job.status = ImportStatus.RUNNING
        job.startedAt = Instant.now()
        importJobRepository.save(job)

        val export = bgStatsFileParser.parse(requireNotNull(job.rawPayload) { "raw payload missing" })
        val matchResult = gameMatcher.match(export.games)
        val playsImported = importMatchedPlays(job.userId, export, matchResult.matchedGameIdByBgStatsGameId, null)
        val summary = ImportSummary(playsImported, matchResult.matchedGameIdByBgStatsGameId.size, matchResult.unmatched)

        completeJob(job, summary)
    }

    /** Re-runs matching with resolved names forced to explicit game ids, importing only plays for those games. */
    fun resolve(job: ImportJobEntity, existingSummary: ImportSummary, nameOverrides: Map<String, Long>): ImportSummary {
        val export = bgStatsFileParser.parse(requireNotNull(job.rawPayload) { "raw payload missing" })
        val resolvedBgStatsGameIds = export.games.filter { it.name in nameOverrides.keys }.map { it.id }.toSet()
        val matchResult = gameMatcher.match(export.games, nameOverrides)
        val newlyImported = importMatchedPlays(job.userId, export, matchResult.matchedGameIdByBgStatsGameId, resolvedBgStatsGameIds)

        return ImportSummary(
            playsImported = existingSummary.playsImported + newlyImported,
            gamesMatched = existingSummary.gamesMatched + nameOverrides.size,
            unmatched = existingSummary.unmatched.filterNot { it.name in nameOverrides.keys },
        )
    }

    private fun importMatchedPlays(
        userId: Long,
        export: BgStatsExport,
        matchedGameIdByBgStatsGameId: Map<Long, Long>,
        onlyForBgStatsGameIds: Set<Long>?,
    ): Int {
        val playerNameById = export.players.associate { it.id to (it.name ?: "Unknown") }
        var playsImported = 0
        for (play in export.plays) {
            if (play.ignored) continue
            if (onlyForBgStatsGameIds != null && play.gameRefId !in onlyForBgStatsGameIds) continue
            val gameId = matchedGameIdByBgStatsGameId[play.gameRefId] ?: continue
            if (importSinglePlay(userId, gameId, play, playerNameById)) playsImported++
        }
        return playsImported
    }

    private fun importSinglePlay(userId: Long, gameId: Long, play: BgStatsPlay, playerNameById: Map<Long, String>): Boolean {
        val playedAt = bgStatsFileParser.parsePlayDate(play.playDate)
        val players = play.playerScores.map {
            ImportPlayerInput(name = playerNameById[it.playerRefId] ?: "Unknown", score = it.score, isWinner = it.winner)
        }
        return playBulkImporter.importPlay(userId, gameId, playedAt, players).created
    }

    private fun completeJob(job: ImportJobEntity, summary: ImportSummary) {
        job.resultSummary = objectMapper.writeValueAsString(summary)
        job.status = ImportStatus.DONE
        job.finishedAt = Instant.now()
        importJobRepository.save(job)
    }

    private fun markFailed(job: ImportJobEntity, ex: Exception) {
        job.status = ImportStatus.FAILED
        job.errorMessage = ex.message ?: ex.javaClass.simpleName
        job.finishedAt = Instant.now()
        importJobRepository.save(job)
    }
}
