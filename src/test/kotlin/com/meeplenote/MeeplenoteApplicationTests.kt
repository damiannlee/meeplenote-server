package com.meeplenote

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 컨텍스트 로드 + Flyway V1 마이그레이션 검증.
 * Testcontainers로 실제 PostgreSQL 15에서 마이그레이션이 성공해야 통과한다.
 */
@SpringBootTest
@Testcontainers
class MeeplenoteApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
    }

    @Test
    fun contextLoads() {
        // 컨텍스트가 뜨고 Flyway 마이그레이션(V1)이 성공하면 통과
    }
}
