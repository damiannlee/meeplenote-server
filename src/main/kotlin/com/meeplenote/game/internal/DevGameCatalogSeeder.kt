package com.meeplenote.game.internal

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** bggId is fabricated for these fixtures; real BGG IDs stay well below this range (M8 is on hold). */
private const val FAKE_BGG_ID_BASE = 900_000_001L

private data class SeedGame(
    val nameKo: String,
    val nameEn: String,
    val minPlayers: Short,
    val maxPlayers: Short,
    val playtimeMinutes: Short,
)

private val SEED_GAMES = listOf(
    SeedGame("카탄", "Catan", 3, 4, 60),
    SeedGame("스플렌더", "Splendor", 2, 4, 30),
    SeedGame("아줄", "Azul", 2, 4, 45),
    SeedGame("티켓 투 라이드", "Ticket to Ride", 2, 5, 60),
    SeedGame("도미니언", "Dominion", 2, 4, 30),
    SeedGame("뱅", "Bang!", 4, 7, 40),
    SeedGame("코드네임", "Codenames", 2, 8, 15),
    SeedGame("다빈치 코드", "Da Vinci Code", 2, 4, 20),
    SeedGame("루미큐브", "Rummikub", 2, 4, 45),
    SeedGame("할리갈리", "Halli Galli", 2, 6, 20),
    SeedGame("윙스팬", "Wingspan", 1, 5, 70),
    SeedGame("스카웃", "Scout", 2, 5, 20),
    SeedGame("텔레스트레이션", "Telestrations", 4, 8, 40),
    SeedGame("세븐 원더스", "7 Wonders", 2, 7, 30),
    SeedGame("스컬킹", "Skull King", 2, 6, 30),
)

/**
 * Populates a local-only game catalog so search (M2) and downstream flows (collection/play/stats)
 * have something to work against. Only registered under the `dev-seed` profile — see
 * docs/local-dev-testing.http for how to activate it. Skips if games already exist (idempotent restart).
 */
@Component
@Profile("dev-seed")
class DevGameCatalogSeeder(
    private val gameRepository: GameRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (gameRepository.count() > 0) return

        SEED_GAMES.forEachIndexed { index, seed ->
            gameRepository.save(
                GameEntity(
                    bggId = FAKE_BGG_ID_BASE + index,
                    source = GameSource.BGG,
                    nameKo = seed.nameKo,
                    nameEn = seed.nameEn,
                    nameInitials = InitialConsonantExtractor.extract(seed.nameKo),
                    minPlayers = seed.minPlayers,
                    maxPlayers = seed.maxPlayers,
                    playtimeMinutes = seed.playtimeMinutes,
                ),
            )
        }
    }
}
