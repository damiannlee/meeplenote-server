package com.meeplenote.auth.internal

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * `dev-seed` 프로필을 켜지 않은 일반 실행(테스트 스위트의 기본 프로필과 동일)에서
 * dev 전용 로그인 엔드포인트가 노출되지 않는지 확인하는 회귀 방지 테스트.
 * dev-seed가 켜졌을 때의 정상 동작은 [DevAuthControllerIntegrationTest] 참조.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DevAuthControllerProfileGatingTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `dev-seed 프로필이 없으면 dev-login은 404다`() {
        mockMvc.perform(post("/api/v1/auth/dev-login"))
            .andExpect(status().isNotFound)
    }
}
