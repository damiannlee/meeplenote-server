package com.meeplenote.auth.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * dev-seed 프로필에서 dev-login이 실제로 동작하고, 발급된 토큰으로 인증이 필요한 API를 호출할 수 있는지 확인.
 * `dev-seed` 미활성 상태의 회귀 방지는 [DevSeedProfileGatingTest] 참조.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local", "dev-seed")
@Testcontainers
class DevAuthControllerIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `dev-login으로 발급받은 토큰으로 인증이 필요한 API를 호출한다`() {
        val loginResult = mockMvc.perform(post("/api/v1/auth/dev-login"))
            .andExpect(status().isOk)
            .andReturn()

        val accessToken = JsonPath.read<String>(loginResult.response.contentAsString, "$.accessToken")
        assertThat(accessToken).isNotBlank()

        mockMvc.perform(get("/api/v1/stats/summary").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
            .andExpect(status().isOk)
    }

    @Test
    fun `같은 dev 유저로 재호출하면 isNewUser가 false다`() {
        mockMvc.perform(post("/api/v1/auth/dev-login")).andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/auth/dev-login"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(false))
    }
}
