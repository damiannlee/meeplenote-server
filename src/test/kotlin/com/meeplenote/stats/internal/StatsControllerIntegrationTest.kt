package com.meeplenote.stats.internal

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
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Obtains a token via the auth module's real HTTP contract and drives plays/collections
 * through their real HTTP contracts to exercise GET /api/v1/stats/summary end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StatsControllerIntegrationTest {

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

    private fun recordPlay(accessToken: String, gameId: Long, playedAt: LocalDate? = null) {
        val playedAtField = playedAt?.let { """, "playedAt": "$it"""" } ?: ""
        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId$playedAtField}"""),
        ).andExpect(status().isCreated)
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
    fun `기록이 없으면 모든 값이 0이고 12개월이 채워진 상태로 응답한다`() {
        val accessToken = issueAccessToken(kakaoId = 2001)

        mockMvc.perform(get("/api/v1/stats/summary").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPlays").value(0))
            .andExpect(jsonPath("$.playsThisMonth").value(0))
            .andExpect(jsonPath("$.monthlyTrend.length()").value(12))
            .andExpect(jsonPath("$.monthlyTrend[11].month").value(YearMonth.now().toString()))
            .andExpect(jsonPath("$.monthlyTrend[11].count").value(0))
            .andExpect(jsonPath("$.topGames.length()").value(0))
            .andExpect(jsonPath("$.noPlayCount").value(0))
    }

    @Test
    fun `기록을 저장하면 totalPlays와 이번달 플레이 수에 반영된다`() {
        val accessToken = issueAccessToken(kakaoId = 2002)
        val gameId = registerGame(accessToken, "카탄")

        recordPlay(accessToken, gameId)
        recordPlay(accessToken, gameId)

        mockMvc.perform(get("/api/v1/stats/summary").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPlays").value(2))
            .andExpect(jsonPath("$.playsThisMonth").value(2))
            .andExpect(jsonPath("$.monthlyTrend[11].count").value(2))
    }

    @Test
    fun `지난달 기록은 지난달 칸에, 이번달 기록은 이번달 칸에 반영된다`() {
        val accessToken = issueAccessToken(kakaoId = 2003)
        val gameId = registerGame(accessToken, "루미큐브")
        val lastMonth = YearMonth.now().minusMonths(1)

        recordPlay(accessToken, gameId, playedAt = lastMonth.atDay(1))
        recordPlay(accessToken, gameId)

        mockMvc.perform(get("/api/v1/stats/summary").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPlays").value(2))
            .andExpect(jsonPath("$.playsThisMonth").value(1))
            .andExpect(jsonPath("$.monthlyTrend[10].month").value(lastMonth.toString()))
            .andExpect(jsonPath("$.monthlyTrend[10].count").value(1))
            .andExpect(jsonPath("$.monthlyTrend[11].count").value(1))
    }

    @Test
    fun `top 게임은 플레이 횟수 내림차순으로 최대 5개 반환되고 게임명이 채워진다`() {
        val accessToken = issueAccessToken(kakaoId = 2004)
        val popularGameId = registerGame(accessToken, "스플렌더")
        val lessPopularGameId = registerGame(accessToken, "코드네임")

        recordPlay(accessToken, popularGameId)
        recordPlay(accessToken, popularGameId)
        recordPlay(accessToken, popularGameId)
        recordPlay(accessToken, lessPopularGameId)

        mockMvc.perform(get("/api/v1/stats/summary").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topGames.length()").value(2))
            .andExpect(jsonPath("$.topGames[0].gameId").value(popularGameId))
            .andExpect(jsonPath("$.topGames[0].count").value(3))
            .andExpect(jsonPath("$.topGames[0].nameKo").value("스플렌더"))
            .andExpect(jsonPath("$.topGames[1].gameId").value(lessPopularGameId))
            .andExpect(jsonPath("$.topGames[1].count").value(1))
    }

    @Test
    fun `보유 후 한 번도 기록이 없는 게임 수가 noPlayCount로 반환된다`() {
        val accessToken = issueAccessToken(kakaoId = 2005)
        val noPlayGameId = registerGame(accessToken, "아그리콜라")
        val playedGameId = registerGame(accessToken, "브라스버밍엄")
        val wishedGameId = registerGame(accessToken, "글룸헤이븐")

        addToCollection(accessToken, noPlayGameId, "OWNED")
        addToCollection(accessToken, playedGameId, "OWNED")
        addToCollection(accessToken, wishedGameId, "WISHED")
        recordPlay(accessToken, playedGameId)

        mockMvc.perform(get("/api/v1/stats/summary").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.noPlayCount").value(1))
    }

    @Test
    fun `다른 유저의 기록은 통계에 반영되지 않는다`() {
        val accessTokenA = issueAccessToken(kakaoId = 2006, nickname = "userA")
        val gameIdA = registerGame(accessTokenA, "테라미스티카")
        recordPlay(accessTokenA, gameIdA)

        val accessTokenB = issueAccessToken(kakaoId = 2007, nickname = "userB")

        mockMvc.perform(get("/api/v1/stats/summary").header("Authorization", "Bearer $accessTokenB"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPlays").value(0))
    }
}
