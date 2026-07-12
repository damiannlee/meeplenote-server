package com.meeplenote.collection.api

interface CollectionLookup {
    fun isOwned(userId: Long, gameId: Long): Boolean
}
