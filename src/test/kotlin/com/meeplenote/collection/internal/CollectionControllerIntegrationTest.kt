package com.meeplenote.collection.internal

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Obtains a token via the auth module's real HTTP contract (Kakao login) and registers
 * a game via the game module's real HTTP contract, then exercises PUT/DELETE /api/v1/collections/{gameId}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CollectionControllerIntegrationTest {

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

    @Test
    fun `없던 게임을 보유로 추가하면 200과 상태를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2001)
        val gameId = registerGame(accessToken, "카탄")

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "OWNED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameId").value(gameId))
            .andExpect(jsonPath("$.status").value("OWNED"))
    }

    @Test
    fun `같은 게임에 다시 PUT하면 상태가 갱신되고 멱등하게 200을 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2002)
        val gameId = registerGame(accessToken, "루미큐브")

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "WISHED"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "OWNED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("OWNED"))
    }

    @Test
    fun `존재하지 않는 게임에 PUT하면 404 GAME_NOT_FOUND를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2003)

        mockMvc.perform(
            put("/api/v1/collections/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "OWNED"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("GAME_NOT_FOUND"))
    }

    @Test
    fun `보유 게임을 DELETE하면 204를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2004)
        val gameId = registerGame(accessToken, "스플렌더")
        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "OWNED"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            delete("/api/v1/collections/$gameId")
                .header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `등록된 적 없는 게임을 DELETE해도 204를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2005)
        val gameId = registerGame(accessToken, "티츄")

        mockMvc.perform(
            delete("/api/v1/collections/$gameId")
                .header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `인증 없이 PUT을 호출하면 401을 반환한다`() {
        mockMvc.perform(
            put("/api/v1/collections/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status": "OWNED"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `status가 잘못된 값이면 422를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2006)
        val gameId = registerGame(accessToken, "아그리콜라")

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "INVALID"}"""),
        ).andExpect(status().isUnprocessableEntity)
    }
}
