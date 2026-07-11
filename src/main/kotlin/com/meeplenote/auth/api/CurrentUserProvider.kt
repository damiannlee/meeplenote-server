package com.meeplenote.auth.api

/** Single access point for the authenticated user's id, reused by any module needing user scope. */
interface CurrentUserProvider {
    fun currentUserId(): Long
}
