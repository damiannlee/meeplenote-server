package com.meeplenote.game.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * `dev-seed` 프로필이 켜졌을 때 게임 카탈로그가 실제로 채워지는지 확인.
 * 미활성 상태의 회귀 방지는 [DevGameCatalogSeederProfileGatingTest] 참조.
 */
@SpringBootTest
@ActiveProfiles("local", "dev-seed")
@Testcontainers
class DevGameCatalogSeederTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
    }

    @Autowired
    lateinit var gameRepository: GameRepository

    @Test
    fun `dev-seed 프로필이면 게임 카탈로그가 채워진다`() {
        val games = gameRepository.findAll()

        assertThat(games).isNotEmpty()
        val catan = games.single { it.nameKo == "카탄" }
        assertThat(catan.nameInitials).isEqualTo(InitialConsonantExtractor.extract("카탄"))
        assertThat(catan.source).isEqualTo(GameSource.BGG)
    }
}
