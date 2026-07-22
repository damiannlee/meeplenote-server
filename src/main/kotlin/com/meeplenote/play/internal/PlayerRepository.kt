package com.meeplenote.play.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlayerRepository : JpaRepository<PlayerEntity, Long> {
    fun findAllByUserIdAndIdIn(userId: Long, ids: Collection<Long>): List<PlayerEntity>
    fun findAllByUserIdAndNameIn(userId: Long, names: Collection<String>): List<PlayerEntity>
    fun findByUserIdAndId(userId: Long, id: Long): PlayerEntity?
    fun findAllByUserIdOrderByIsFavoriteDescNameAsc(userId: Long): List<PlayerEntity>

    @Query(
        value = """
            SELECT p.* FROM players p
            JOIN (
                SELECT pp.player_id, MAX(pl.played_at) AS last_played_at
                FROM play_players pp
                JOIN plays pl ON pl.id = pp.play_id
                WHERE pl.user_id = :userId
                GROUP BY pp.player_id
            ) recent ON recent.player_id = p.id
            ORDER BY recent.last_played_at DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentlyPlayedWith(@Param("userId") userId: Long, @Param("limit") limit: Int): List<PlayerEntity>
}
