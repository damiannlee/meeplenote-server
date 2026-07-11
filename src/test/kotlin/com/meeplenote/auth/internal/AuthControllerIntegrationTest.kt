package com.meeplenote.auth.internal

import com.jayway.jsonpath.JsonPath
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * /auth/social → /auth/refresh 전체 플로우 + 인증 실패 케이스.
 * 카카오는 실제 호출 대신 MockWebServer로 스텁한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")

        private val kakaoMockServer = MockWebServer()

        @JvmStatic
        @BeforeAll
        fun startKakaoMockServer() {
            kakaoMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopKakaoMockServer() {
            kakaoMockServer.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun kakaoProperties(registry: DynamicPropertyRegistry) {
            registry.add("kakao.base-uri") { kakaoMockServer.url("/").toString().removeSuffix("/") }
        }

        private fun kakaoUserResponse(id: Long, nickname: String) = MockResponse()
            .setBody("""{"id": $id, "kakao_account": {"profile": {"nickname": "$nickname"}}}""")
            .addHeader("Content-Type", "application/json")
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `카카오 로그인 후 refresh로 access 토큰을 재발급받는다`() {
        kakaoMockServer.enqueue(kakaoUserResponse(id = 123456, nickname = "테스터"))

        val loginResult = mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao", "token": "dummy-kakao-token"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(true))
            .andReturn()

        val loginBody = loginResult.response.contentAsString
        val accessToken = JsonPath.read<String>(loginBody, "$.accessToken")
        val refreshToken = JsonPath.read<String>(loginBody, "$.refreshToken")
        assertThat(accessToken).isNotBlank()
        assertThat(refreshToken).isNotBlank()

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
    }

    @Test
    fun `동일 카카오 유저로 재로그인하면 isNewUser가 false다`() {
        kakaoMockServer.enqueue(kakaoUserResponse(id = 777777, nickname = "재방문"))
        kakaoMockServer.enqueue(kakaoUserResponse(id = 777777, nickname = "재방문"))

        mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao", "token": "first-login"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao", "token": "second-login"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isNewUser").value(false))
    }

    @Test
    fun `유효하지 않은 refresh 토큰은 401과 TOKEN_EXPIRED를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "no-such-token"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"))
    }

    @Test
    fun `Authorization 헤더 없이 보호된 경로를 호출하면 401을 반환한다`() {
        mockMvc.perform(post("/api/v1/some-protected-endpoint"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `지원하지 않는 provider는 422와 UNSUPPORTED_PROVIDER를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "naver", "token": "x"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_PROVIDER"))
    }

    @Test
    fun `필수 필드가 누락된 요청은 422와 VALIDATION_FAILED를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao"}"""),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }
}
