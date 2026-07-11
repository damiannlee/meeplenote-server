package com.meeplenote.game.internal

import com.jayway.jsonpath.JsonPath
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Obtains a token via the auth module's real HTTP contract (Kakao login), then
 * verifies /api/v1/games local search and custom registration. BGG on-demand
 * lookup is tracked separately (see docs/adr/ADR-003) and is not covered here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GameControllerIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15-alpine")

        private val kakaoMockServer = MockWebServer()

        @JvmStatic
        @BeforeAll
        fun startMockServer() {
            kakaoMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMockServer() {
            kakaoMockServer.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun mockServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("kakao.base-uri") { kakaoMockServer.url("/").toString().removeSuffix("/") }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun issueAccessToken(): String {
        kakaoMockServer.enqueue(
            MockResponse()
                .setBody("""{"id": 555, "kakao_account": {"profile": {"nickname": "게임유저"}}}""")
                .addHeader("Content-Type", "application/json"),
        )
        val loginBody = mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao", "token": "dummy-token"}"""),
        ).andReturn().response.contentAsString
        return JsonPath.read(loginBody, "$.accessToken")
    }

    @Test
    fun `커스텀 등록한 게임은 이름으로 검색된다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            post("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "우리집 자작 게임"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/games")
                .param("q", "우리집 자작 게임")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].nameKo").value("우리집 자작 게임"))
            .andExpect(jsonPath("$.items[0].source").value("custom"))
    }

    @Test
    fun `일치하는 게임이 없으면 200과 빈 배열을 반환한다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            get("/api/v1/games")
                .param("q", "존재하지않는게임이름xyz")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun `검색어가 비어있으면 400 QUERY_TOO_SHORT를 반환한다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            get("/api/v1/games")
                .param("q", "")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("QUERY_TOO_SHORT"))
    }

    @Test
    fun `인증 없이 검색하면 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/games").param("q", "catan"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `커스텀 게임 등록은 201과 source custom을 반환한다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            post("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "다른 자작 게임"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.source").value("custom"))
    }

    @Test
    fun `공백 이름으로 커스텀 게임을 등록하면 422를 반환한다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            post("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "  "}"""),
        )
            .andExpect(status().isUnprocessableEntity)
    }
}
