package com.meeplenote.collection.api

import java.time.Instant
import java.time.LocalDate

data class CollectionExportRecord(
    val gameId: Long,
    val status: String,
    val playCount: Int,
    val lastPlayedAt: LocalDate?,
    val addedAt: Instant,
)

interface CollectionLookup {
    fun isOwned(userId: Long, gameId: Long): Boolean
    fun countNoPlay(userId: Long): Long
    fun getAllForUser(userId: Long): List<CollectionExportRecord>
}
