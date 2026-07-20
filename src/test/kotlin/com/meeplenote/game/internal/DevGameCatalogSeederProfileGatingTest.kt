package com.meeplenote.game.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * `dev-seed` 프로필을 켜지 않은 일반 실행(테스트 스위트의 기본 프로필과 동일)에서
 * 게임 카탈로그 시더가 돌지 않는지 확인하는 회귀 방지 테스트 — 다른 통합 테스트들이
 * 빈 games 테이블을 전제로 검색 결과 개수를 단언하므로, 실수로 항상 켜지면 그 테스트들이 깨진다.
 */
@SpringBootTest
@Testcontainers
class DevGameCatalogSeederProfileGatingTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
    }

    @Autowired
    lateinit var gameRepository: GameRepository

    @Test
    fun `dev-seed 프로필이 없으면 게임 카탈로그가 시드되지 않는다`() {
        assertThat(gameRepository.count()).isZero()
    }
}
