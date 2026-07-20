package com.meeplenote.play.internal

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface MonthlyPlayCountRow {
    fun getMonth(): String
    fun getPlayCount(): Long
}

interface GamePlayCountRow {
    fun getGameId(): Long
    fun getPlayCount(): Long
}

interface PlayRepository : JpaRepository<PlayEntity, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: UUID): PlayEntity?
    fun findAllByUserIdAndGameIdAndPlayedAt(userId: Long, gameId: Long, playedAt: LocalDate): List<PlayEntity>
    fun findAllByUserId(userId: Long, sort: Sort): List<PlayEntity>
    fun countByUserIdAndGameId(userId: Long, gameId: Long): Int
    fun countByUserId(userId: Long): Long
    fun countByUserIdAndPlayedAtGreaterThanEqual(userId: Long, from: LocalDate): Long

    @Query(
        value = """
            SELECT to_char(played_at, 'YYYY-MM') AS month, COUNT(*) AS play_count
            FROM plays
            WHERE user_id = :userId AND played_at >= :from
            GROUP BY month
        """,
        nativeQuery = true,
    )
    fun countGroupedByMonth(@Param("userId") userId: Long, @Param("from") from: LocalDate): List<MonthlyPlayCountRow>

    @Query(
        value = """
            SELECT game_id AS game_id, COUNT(*) AS play_count
            FROM plays
            WHERE user_id = :userId
            GROUP BY game_id
            ORDER BY play_count DESC, game_id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findTopGamesByPlayCount(@Param("userId") userId: Long, @Param("limit") limit: Int): List<GamePlayCountRow>

    @Query(
        value = """
            SELECT * FROM plays
            WHERE user_id = :userId
            ORDER BY played_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findFirstPageByUserId(@Param("userId") userId: Long, @Param("limit") limit: Int): List<PlayEntity>

    @Query(
        value = """
            SELECT * FROM plays
            WHERE user_id = :userId
              AND (played_at < :cursorPlayedAt OR (played_at = :cursorPlayedAt AND id < :cursorId))
            ORDER BY played_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findNextPageByUserId(
        @Param("userId") userId: Long,
        @Param("cursorPlayedAt") cursorPlayedAt: LocalDate,
        @Param("cursorId") cursorId: Long,
        @Param("limit") limit: Int,
    ): List<PlayEntity>
}
