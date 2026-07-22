package com.meeplenote.play.internal

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
import org.springframework.jdbc.core.JdbcTemplate
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
 * Obtains a token via the auth module's real HTTP contract (Kakao login) and registers
 * a game via the game module's real HTTP contract, then exercises POST /api/v1/plays.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlayControllerIntegrationTest {

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

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

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
    fun `최소 필드로 기록하면 201과 오늘 날짜를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1001)
        val gameId = registerGame(accessToken, "카탄")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.playedAt").value(LocalDate.now().toString()))
            .andExpect(jsonPath("$.totalPlayCountForGame").value(1))
            .andExpect(jsonPath("$.suggestAddToCollection").value(true))
    }

    @Test
    fun `플레이어 목록을 포함해 기록하면 성공한다`() {
        val accessToken = issueAccessToken(kakaoId = 1002)
        val gameId = registerGame(accessToken, "루미큐브")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(
                    """{"gameId": $gameId, "players": [
                        {"name": "철수", "score": 10, "isWinner": true},
                        {"name": "영희", "score": 8}
                    ]}""",
                ),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "players": [{"name": "철수", "score": 5}]}"""),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `동일 Idempotency-Key로 재요청하면 최초 기록을 그대로 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1003)
        val gameId = registerGame(accessToken, "스플렌더")
        val idempotencyKey = UUID.randomUUID().toString()

        val firstBody = mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", idempotencyKey)
                .content("""{"gameId": $gameId}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val firstId = JsonPath.read<Int>(firstBody, "$.id")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", idempotencyKey)
                .content("""{"gameId": $gameId}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(firstId))
    }

    @Test
    fun `존재하지 않는 gameId로 기록하면 404 GAME_NOT_FOUND를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1004)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": 999999}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("GAME_NOT_FOUND"))
    }

    @Test
    fun `Idempotency-Key 헤더 없이 요청하면 400을 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1005)
        val gameId = registerGame(accessToken, "티츄")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"gameId": $gameId}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("MISSING_HEADER"))
    }

    @Test
    fun `미래 playedAt으로 기록하면 422를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1006)
        val gameId = registerGame(accessToken, "아그리콜라")
        val tomorrow = LocalDate.now().plusDays(1)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "playedAt": "$tomorrow"}"""),
        ).andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `rating이 범위를 벗어나면 422를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1007)
        val gameId = registerGame(accessToken, "윙스팬")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "rating": 6}"""),
        ).andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `같은 게임 두 번 기록하면 두 번째 응답의 totalPlayCountForGame은 2다`() {
        val accessToken = issueAccessToken(kakaoId = 1008)
        val gameId = registerGame(accessToken, "테라포밍마스")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.totalPlayCountForGame").value(2))
    }

    @Test
    fun `보유 컬렉션이 있으면 suggestAddToCollection이 false를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1009)
        val gameId = registerGame(accessToken, "글룸헤이븐")

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "OWNED"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.suggestAddToCollection").value(false))
    }

    @Test
    fun `기록을 저장하면 컬렉션의 플레이 횟수와 노플 배지가 갱신된다`() {
        val accessToken = issueAccessToken(kakaoId = 1012)
        val gameId = registerGame(accessToken, "테라미스티카")

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "OWNED"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/collections")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(jsonPath("$.items[0].playCount").value(0))
            .andExpect(jsonPath("$.items[0].isNoPlay").value(true))

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/collections")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(jsonPath("$.items[0].playCount").value(1))
            .andExpect(jsonPath("$.items[0].isNoPlay").value(false))
    }

    @Test
    fun `위시리스트 게임을 기록하면 playCount는 갱신되지만 노플 배지는 붙지 않는다`() {
        val accessToken = issueAccessToken(kakaoId = 1013)
        val gameId = registerGame(accessToken, "아컴호러")

        mockMvc.perform(
            put("/api/v1/collections/$gameId")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"status": "WISHED"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/collections")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(jsonPath("$.items[0].status").value("WISHED"))
            .andExpect(jsonPath("$.items[0].playCount").value(1))
            .andExpect(jsonPath("$.items[0].isNoPlay").value(false))
    }

    @Test
    fun `타 유저가 등록한 playerId를 지정하면 404를 반환한다`() {
        val accessTokenA = issueAccessToken(kakaoId = 1010, nickname = "userA")
        val gameIdA = registerGame(accessTokenA, "브라스버밍엄")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessTokenA")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameIdA, "players": [{"name": "userA친구"}]}"""),
        ).andExpect(status().isCreated)

        val playerIdOfUserA = jdbcTemplate.queryForObject(
            "SELECT id FROM players WHERE name = 'userA친구'",
            Long::class.java,
        )

        val accessTokenB = issueAccessToken(kakaoId = 1011, nickname = "userB")
        val gameIdB = registerGame(accessTokenB, "이스투리아")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessTokenB")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameIdB, "players": [{"playerId": $playerIdOfUserA}]}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PLAYER_NOT_FOUND"))
    }

    @Test
    fun `목록을 조회하면 게임명과 썸네일이 포함된 최신순 기록을 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1014)
        val gameId = registerGame(accessToken, "엘 그란데")

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/plays")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].gameId").value(gameId))
            .andExpect(jsonPath("$.items[0].gameName").value("엘 그란데"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `limit보다 기록이 많으면 nextCursor로 다음 페이지를 이어서 조회할 수 있다`() {
        val accessToken = issueAccessToken(kakaoId = 1015)
        val gameId = registerGame(accessToken, "칸반")
        repeat(3) {
            mockMvc.perform(
                post("/api/v1/plays")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content("""{"gameId": $gameId, "playedAt": "${LocalDate.now().minusDays(it.toLong())}"}"""),
            ).andExpect(status().isCreated)
        }

        val firstPage = mockMvc.perform(
            get("/api/v1/plays?limit=2")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn().response.contentAsString
        val nextCursor = JsonPath.read<String>(firstPage, "$.nextCursor")

        mockMvc.perform(
            get("/api/v1/plays?limit=2&cursor=$nextCursor")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `타 유저의 기록은 목록에 노출되지 않는다`() {
        val accessTokenA = issueAccessToken(kakaoId = 1016, nickname = "userC")
        val gameIdA = registerGame(accessTokenA, "루나")
        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessTokenA")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameIdA}"""),
        ).andExpect(status().isCreated)

        val accessTokenB = issueAccessToken(kakaoId = 1017, nickname = "userD")

        mockMvc.perform(
            get("/api/v1/plays")
                .header("Authorization", "Bearer $accessTokenB"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `인증 없이 목록을 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/plays")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `limit이 50을 초과하면 400 INVALID_PARAMETER를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1018)

        mockMvc.perform(
            get("/api/v1/plays?limit=51")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `잘못된 형식의 cursor면 400 INVALID_CURSOR를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1019)

        mockMvc.perform(
            get("/api/v1/plays?cursor=not-a-valid-cursor")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"))
    }

    @Test
    fun `yearMonth로 캘린더를 조회하면 해당 월의 기록만 오래된 순으로 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1020)
        val gameId = registerGame(accessToken, "타지마할")
        val thisMonth = LocalDate.now().withDayOfMonth(1)
        val lastMonth = thisMonth.minusMonths(1).withDayOfMonth(1)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "playedAt": "$lastMonth"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "playedAt": "$thisMonth"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            get("/api/v1/plays/calendar?yearMonth=${YearMonth.from(thisMonth)}")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].playedAt").value(thisMonth.toString()))
            .andExpect(jsonPath("$.items[0].gameName").value("타지마할"))
    }

    @Test
    fun `캘린더 조회에서 타 유저의 기록은 노출되지 않는다`() {
        val accessTokenA = issueAccessToken(kakaoId = 1021, nickname = "userE")
        val gameIdA = registerGame(accessTokenA, "위키드")
        val thisMonth = LocalDate.now().withDayOfMonth(1)
        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessTokenA")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameIdA, "playedAt": "$thisMonth"}"""),
        ).andExpect(status().isCreated)

        val accessTokenB = issueAccessToken(kakaoId = 1022, nickname = "userF")

        mockMvc.perform(
            get("/api/v1/plays/calendar?yearMonth=${YearMonth.from(thisMonth)}")
                .header("Authorization", "Bearer $accessTokenB"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `yearMonth 형식이 올바르지 않으면 400 INVALID_PARAMETER를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1023)

        mockMvc.perform(
            get("/api/v1/plays/calendar?yearMonth=2026-13")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"))
    }

    @Test
    fun `yearMonth 없이 캘린더를 조회하면 400 MISSING_PARAMETER를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 1024)

        mockMvc.perform(
            get("/api/v1/plays/calendar")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"))
    }
}
