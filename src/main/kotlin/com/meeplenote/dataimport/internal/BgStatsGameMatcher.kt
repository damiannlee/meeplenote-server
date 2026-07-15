package com.meeplenote.dataimport.internal

import com.meeplenote.game.api.GameLookup
import com.meeplenote.game.api.GameSummary
import org.springframework.stereotype.Component

data class GameMatchResult(
    val matchedGameIdByBgStatsGameId: Map<Long, Long>,
    val unmatched: List<UnmatchedGame>,
)

private const val CANDIDATE_LIMIT = 3

@Component
class BgStatsGameMatcher(
    private val gameLookup: GameLookup,
) {

    fun match(games: List<BgStatsGame>, nameOverrides: Map<String, Long> = emptyMap()): GameMatchResult {
        val matched = mutableMapOf<Long, Long>()
        val unmatched = mutableListOf<UnmatchedGame>()

        for (game in games) {
            val override = game.name?.let { nameOverrides[it] }
            val byBggId = game.bggId?.takeIf { it != 0L }?.let { gameLookup.findByBggId(it) }
            when {
                override != null -> matched[game.id] = override
                byBggId != null -> matched[game.id] = byBggId.id
                else -> resolveByName(game, matched, unmatched)
            }
        }

        return GameMatchResult(matched, unmatched)
    }

    private fun resolveByName(game: BgStatsGame, matched: MutableMap<Long, Long>, unmatched: MutableList<UnmatchedGame>) {
        val name = game.name
        if (name.isNullOrBlank()) {
            unmatched.add(UnmatchedGame(name = "(이름 없음)", candidates = emptyList()))
            return
        }
        val candidates = gameLookup.findCandidatesByName(name, CANDIDATE_LIMIT)
        val exact = candidates.firstOrNull { it.nameKo == name || it.nameEn == name }
        if (exact != null) {
            matched[game.id] = exact.id
        } else {
            unmatched.add(UnmatchedGame(name = name, candidates = candidates.map { it.toCandidate() }))
        }
    }

    private fun GameSummary.toCandidate() = GameCandidate(gameId = id, name = nameKo ?: nameEn ?: "")
}
