package com.meeplenote.collection.api

import java.time.LocalDate

interface CollectionPlayTracker {
    fun recordPlay(userId: Long, gameId: Long, playedAt: LocalDate)
}
