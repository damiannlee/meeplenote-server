package com.meeplenote.dataimport.internal

data class GameCandidate(
    val gameId: Long,
    val name: String,
)

data class UnmatchedGame(
    val name: String,
    val candidates: List<GameCandidate>,
)

data class ImportSummary(
    val playsImported: Int,
    val gamesMatched: Int,
    val unmatched: List<UnmatchedGame>,
)
