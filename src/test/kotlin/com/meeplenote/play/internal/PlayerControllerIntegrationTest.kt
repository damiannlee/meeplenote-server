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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Obtains a token via the auth module's real HTTP contract (Kakao login) and creates plays
 * with named players to seed the players table, then exercises the player/group endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlayerControllerIntegrationTest {

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

    private fun recordPlay(accessToken: String, gameId: Long, playerNames: List<String>, playedAt: String? = null) {
        val players = playerNames.joinToString(",") { """{"name": "$it"}""" }
        val playedAtField = playedAt?.let { """, "playedAt": "$it"""" } ?: ""
        mockMvc.perform(
            post("/api/v1/plays")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""{"gameId": $gameId, "players": [$players]$playedAtField}"""),
        ).andExpect(status().isCreated)
    }

    private fun findPlayerId(accessToken: String, name: String): Long {
        val body = mockMvc.perform(
            get("/api/v1/players").header("Authorization", "Bearer $accessToken"),
        ).andReturn().response.contentAsString
        val ids = JsonPath.read<List<Int>>(body, "$[?(@.name=='$name')].id")
        return ids.first().toLong()
    }

    @Test
    fun `명부를 조회하면 이름과 즐겨찾기 여부가 포함된다`() {
        val accessToken = issueAccessToken(kakaoId = 2001)
        val gameId = registerGame(accessToken, "카탄")
        recordPlay(accessToken, gameId, listOf("철수", "영희"))

        mockMvc.perform(get("/api/v1/players").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.name=='철수')].isFavorite").value(false))
    }

    @Test
    fun `즐겨찾기로 지정하면 명부에서 즐겨찾기 여부가 true로 바뀐다`() {
        val accessToken = issueAccessToken(kakaoId = 2002)
        val gameId = registerGame(accessToken, "루미큐브")
        recordPlay(accessToken, gameId, listOf("철수"))
        val playerId = findPlayerId(accessToken, "철수")

        mockMvc.perform(
            patch("/api/v1/players/$playerId/favorite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"isFavorite": true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isFavorite").value(true))
    }

    @Test
    fun `타 유저 소유 플레이어의 즐겨찾기를 변경하면 404를 반환한다`() {
        val accessTokenA = issueAccessToken(kakaoId = 2003, nickname = "userA")
        val gameIdA = registerGame(accessTokenA, "브라스")
        recordPlay(accessTokenA, gameIdA, listOf("userA친구"))
        val playerIdOfA = findPlayerId(accessTokenA, "userA친구")

        val accessTokenB = issueAccessToken(kakaoId = 2004, nickname = "userB")

        mockMvc.perform(
            patch("/api/v1/players/$playerIdOfA/favorite")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessTokenB")
                .content("""{"isFavorite": true}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PLAYER_NOT_FOUND"))
    }

    @Test
    fun `최근 함께 플레이한 사람을 최신순으로 조회한다`() {
        val accessToken = issueAccessToken(kakaoId = 2005)
        val gameId = registerGame(accessToken, "스플렌더")
        recordPlay(accessToken, gameId, listOf("철수"), playedAt = "2026-01-01")
        recordPlay(accessToken, gameId, listOf("영희"), playedAt = "2026-01-10")

        mockMvc.perform(
            get("/api/v1/players/recent?limit=2").header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("영희"))
            .andExpect(jsonPath("$[1].name").value("철수"))
    }

    @Test
    fun `그룹을 생성하고 조회할 수 있다`() {
        val accessToken = issueAccessToken(kakaoId = 2006)

        val body = mockMvc.perform(
            post("/api/v1/player-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "동아리"}"""),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val groupId = JsonPath.read<Int>(body, "$.id")

        mockMvc.perform(get("/api/v1/player-groups").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(groupId))
            .andExpect(jsonPath("$[0].name").value("동아리"))
    }

    @Test
    fun `같은 이름의 그룹을 다시 생성하면 409를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2007)
        mockMvc.perform(
            post("/api/v1/player-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "동아리"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/player-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "동아리"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("PLAYER_GROUP_NAME_DUPLICATE"))
    }

    @Test
    fun `그룹에 플레이어를 추가하고 제거할 수 있다`() {
        val accessToken = issueAccessToken(kakaoId = 2008)
        val gameId = registerGame(accessToken, "아그리콜라")
        recordPlay(accessToken, gameId, listOf("철수"))
        val playerId = findPlayerId(accessToken, "철수")
        val groupBody = mockMvc.perform(
            post("/api/v1/player-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "가족"}"""),
        ).andReturn().response.contentAsString
        val groupId = JsonPath.read<Int>(groupBody, "$.id")

        mockMvc.perform(
            put("/api/v1/player-groups/$groupId/players/$playerId")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.players[0].name").value("철수"))

        mockMvc.perform(
            delete("/api/v1/player-groups/$groupId/players/$playerId")
                .header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/player-groups").header("Authorization", "Bearer $accessToken"))
            .andExpect(jsonPath("$[0].players.length()").value(0))
    }

    @Test
    fun `타 유저 소유 그룹에 플레이어를 추가하면 404를 반환한다`() {
        val accessTokenA = issueAccessToken(kakaoId = 2009, nickname = "userC")
        val groupBody = mockMvc.perform(
            post("/api/v1/player-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessTokenA")
                .content("""{"name": "동호회"}"""),
        ).andReturn().response.contentAsString
        val groupId = JsonPath.read<Int>(groupBody, "$.id")

        val accessTokenB = issueAccessToken(kakaoId = 2010, nickname = "userD")
        val gameIdB = registerGame(accessTokenB, "위키드")
        recordPlay(accessTokenB, gameIdB, listOf("userD친구"))
        val playerIdOfB = findPlayerId(accessTokenB, "userD친구")

        mockMvc.perform(
            put("/api/v1/player-groups/$groupId/players/$playerIdOfB")
                .header("Authorization", "Bearer $accessTokenB"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("PLAYER_GROUP_NOT_FOUND"))
    }

    @Test
    fun `그룹을 삭제할 수 있다`() {
        val accessToken = issueAccessToken(kakaoId = 2011)
        val groupBody = mockMvc.perform(
            post("/api/v1/player-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"name": "삭제될그룹"}"""),
        ).andReturn().response.contentAsString
        val groupId = JsonPath.read<Int>(groupBody, "$.id")

        mockMvc.perform(delete("/api/v1/player-groups/$groupId").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/player-groups").header("Authorization", "Bearer $accessToken"))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `인증 없이 명부를 조회하면 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/players")).andExpect(status().isUnauthorized)
    }
}
