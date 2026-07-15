package com.meeplenote.game.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GameRepository : JpaRepository<GameEntity, Long> {

    fun findAllByIdIn(ids: Collection<Long>): List<GameEntity>

    fun findByBggId(bggId: Long): GameEntity?

    @Query(
        value = """
            SELECT * FROM games
            WHERE name_initials ILIKE '%' || :q || '%'
            ORDER BY length(name_initials)
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun searchByInitials(@Param("q") q: String, @Param("limit") limit: Int): List<GameEntity>

    @Query(
        value = """
            SELECT * FROM games
            WHERE name_ko % :q OR name_en % :q
               OR name_ko ILIKE :q || '%' OR name_en ILIKE :q || '%'
            ORDER BY GREATEST(similarity(coalesce(name_ko, ''), :q), similarity(coalesce(name_en, ''), :q)) DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun searchByName(@Param("q") q: String, @Param("limit") limit: Int): List<GameEntity>
}
