package com.meeplenote.game.internal

import com.jayway.jsonpath.JsonPath
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
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
import kotlin.math.abs

/**
 * Obtains a token via the auth module's real HTTP contract (Kakao login), then
 * verifies /api/v1/games search and registration. BGG is stubbed on the same
 * MockWebServer via a path-based Dispatcher — one server absorbs both
 * external dependencies (Kakao and BGG).
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

        private val mockServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/v2/user/me") -> kakaoUserResponse()
                        path.startsWith("/search") -> bggSearchResponse(request)
                        path.startsWith("/thing") -> bggThingResponse(request)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        }

        // id -> query text, so thing's response name actually matches the original query
        // (otherwise a repeated identical search would never hit the local trgm/ILIKE cache,
        // which is the exact behavior these tests need to verify).
        private val idToQuery = java.util.concurrent.ConcurrentHashMap<String, String>()

        private fun kakaoUserResponse() = MockResponse()
            .setBody("""{"id": 555, "kakao_account": {"profile": {"nickname": "게임유저"}}}""")
            .addHeader("Content-Type", "application/json")

        private fun bggSearchResponse(request: RecordedRequest): MockResponse {
            val query = request.requestUrl?.queryParameter("query") ?: "unknown"
            val id = abs(query.hashCode())
            idToQuery[id.toString()] = query
            return MockResponse()
                .setBody(
                    """
                    <items total="1">
                        <item type="boardgame" id="$id">
                            <name type="primary" value="${query.uppercase()}"/>
                        </item>
                    </items>
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "text/xml")
        }

        private fun bggThingResponse(request: RecordedRequest): MockResponse {
            val id = request.requestUrl?.queryParameter("id")?.split(",")?.first() ?: "0"
            val name = idToQuery[id]?.uppercase() ?: "GAME-$id"
            return MockResponse()
                .setBody(
                    """
                    <items>
                        <item type="boardgame" id="$id">
                            <thumbnail>https://thumb.jpg</thumbnail>
                            <name type="primary" value="$name" />
                            <minplayers value="3" />
                            <maxplayers value="4" />
                            <playingtime value="120" />
                        </item>
                    </items>
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "text/xml")
        }

        @JvmStatic
        @BeforeAll
        fun startMockServer() {
            mockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMockServer() {
            mockServer.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun mockServerProperties(registry: DynamicPropertyRegistry) {
            val baseUrl = mockServer.url("/").toString().removeSuffix("/")
            registry.add("kakao.base-uri") { baseUrl }
            registry.add("bgg.base-uri") { baseUrl }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun issueAccessToken(): String {
        val loginBody = mockMvc.perform(
            post("/api/v1/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "kakao", "token": "dummy-token"}"""),
        ).andReturn().response.contentAsString
        return JsonPath.read(loginBody, "$.accessToken")
    }

    @Test
    fun `캐시 미스 검색은 BGG 결과를 병합해 반환한다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            get("/api/v1/games")
                .param("q", "catan-검색어")
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].source").value("bgg"))
    }

    @Test
    fun `같은 검색어를 다시 조회하면 로컬 캐시로 응답하고 BGG를 다시 부르지 않는다`() {
        val accessToken = issueAccessToken()

        mockMvc.perform(
            get("/api/v1/games").param("q", "repeat-검색어").header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isOk)

        val requestsAfterFirstSearch = mockServer.requestCount
        mockMvc.perform(
            get("/api/v1/games").param("q", "repeat-검색어").header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isOk)

        assertThat(mockServer.requestCount).isEqualTo(requestsAfterFirstSearch)
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
                .content("""{"name": "우리집 자작 게임"}"""),
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
