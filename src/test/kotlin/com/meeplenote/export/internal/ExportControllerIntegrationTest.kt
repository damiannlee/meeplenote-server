package com.meeplenote.export.internal

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Obtains a token via the auth module's real HTTP contract and drives plays/collections
 * through their real HTTP contracts to exercise GET /api/v1/exports end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExportControllerIntegrationTest {

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

    private fun issueAccessToken(kakaoId: Long, nickname: String = "게임유저"): String {
        kakaoMockServer.enqueue(
            MockResponse()
                .setBody("""{"id": $kakaoId, "kakao_account": {"profile": {"nickname": "$nickname"}}}""")
                .addHeader("Content-Type", "application/json"),
        )
        val loginBody = mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao", "token": "dummy-token"}"""),
        ).andReturn().response.contentAsString
        return JsonPath.read(loginBody, "$.accessToken")
    }

    private fun registerGame(accessToken: String, name: String): Long {
        val body = mockMvc.perform(
            post("/api/v1/games")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "$name"}"""),
        ).andReturn().response.contentAsString
        return JsonPath.read<Int>(body, "$.id").toLong()
    }

    private fun recordPlay(accessToken: String, gameId: Long): Long {
        val body = mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "players": [{"name": "민석", "score": 30, "isWinner": true}]}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return JsonPath.read<Int>(body, "$.id").toLong()
    }

    private fun addToCollection(accessToken: String, gameId: Long, status: String) {
        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "$status"}"""),
        ).andExpect(status().isOk)
    }

    @Test
    fun `인증 없이 요청하면 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/exports")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `기록도 컬렉션도 없으면 빈 배열로 응답한다`() {
        val accessToken = issueAccessToken(kakaoId = 3001)

        mockMvc.perform(get("/api/v1/exports").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plays.length()").value(0))
            .andExpect(jsonPath("$.collections.length()").value(0))
    }

    @Test
    fun `플레이 기록과 컬렉션을 게임명과 함께 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 3002)
        val gameId = registerGame(accessToken, "카탄")
        recordPlay(accessToken, gameId)
        addToCollection(accessToken, gameId, "OWNED")

        mockMvc.perform(get("/api/v1/exports").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plays.length()").value(1))
            .andExpect(jsonPath("$.plays[0].gameId").value(gameId))
            .andExpect(jsonPath("$.plays[0].gameName").value("카탄"))
            .andExpect(jsonPath("$.plays[0].players.length()").value(1))
            .andExpect(jsonPath("$.plays[0].players[0].name").value("민석"))
            .andExpect(jsonPath("$.collections.length()").value(1))
            .andExpect(jsonPath("$.collections[0].gameId").value(gameId))
            .andExpect(jsonPath("$.collections[0].gameName").value("카탄"))
            .andExpect(jsonPath("$.collections[0].status").value("OWNED"))
    }

    @Test
    fun `다른 유저의 기록과 컬렉션은 노출되지 않는다`() {
        val accessTokenA = issueAccessToken(kakaoId = 3003, nickname = "userA")
        val gameIdA = registerGame(accessTokenA, "루미큐브")
        recordPlay(accessTokenA, gameIdA)
        addToCollection(accessTokenA, gameIdA, "OWNED")

        val accessTokenB = issueAccessToken(kakaoId = 3004, nickname = "userB")

        mockMvc.perform(get("/api/v1/exports").header("Authorization", "Bearer $accessTokenB"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plays.length()").value(0))
            .andExpect(jsonPath("$.collections.length()").value(0))
    }
}
