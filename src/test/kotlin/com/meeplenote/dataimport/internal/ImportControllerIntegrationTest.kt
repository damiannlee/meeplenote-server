package com.meeplenote.dataimport.internal

import com.jayway.jsonpath.JsonPath
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ImportControllerIntegrationTest {

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

    private fun submitFile(accessToken: String, json: String): String =
        mockMvc.perform(
            multipart("/api/v1/imports")
                .file(MockMultipartFile("file", "export.json", "application/json", json.toByteArray()))
                .header("Authorization", "Bearer $accessToken"),
        ).andExpect(status().isAccepted).andReturn().response.contentAsString

    private fun waitUntilDone(accessToken: String, jobId: Int): String {
        repeat(50) {
            val body = mockMvc.perform(
                get("/api/v1/imports/$jobId")
                    .header("Authorization", "Bearer $accessToken"),
            ).andReturn().response.contentAsString
            val status: String = JsonPath.read(body, "$.status")
            if (status == "done" || status == "failed") return body
            Thread.sleep(100)
        }
        throw AssertionError("import job $jobId did not finish in time")
    }

    @Test
    fun `이름이 정확히 일치하는 게임은 매칭되어 플레이가 생성된다`() {
        val accessToken = issueAccessToken(kakaoId = 2001)
        val gameId = registerGame(accessToken, "루미큐브")

        val json = """
            {
              "games": [{"id": 1, "bggId": 0, "name": "루미큐브"}],
              "players": [{"id": 1, "name": "철수"}, {"id": 2, "name": "영희"}],
              "plays": [{"uuid": "u1", "gameRefId": 1, "playDate": "2026-07-01 10:00:00", "ignored": false,
                         "playerScores": [{"playerRefId": 1, "score": 10, "winner": true}, {"playerRefId": 2, "score": 8, "winner": false}]}]
            }
        """.trimIndent()

        val submitBody = submitFile(accessToken, json)
        val jobId: Int = JsonPath.read(submitBody, "$.jobId")

        val doneBody = waitUntilDone(accessToken, jobId)
        val doneStatus: String = JsonPath.read(doneBody, "$.status")
        assertThat(doneStatus).isEqualTo("done")
        assertThat(JsonPath.read<Int>(doneBody, "$.summary.playsImported")).isEqualTo(1)
        assertThat(JsonPath.read<Int>(doneBody, "$.summary.gamesMatched")).isEqualTo(1)

        val playCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plays WHERE game_id = ?",
            Int::class.java,
            gameId,
        )
        assertThat(playCount).isEqualTo(1)
    }

    @Test
    fun `bggId로 등록된 게임은 이름 없이도 매칭된다`() {
        val accessToken = issueAccessToken(kakaoId = 2002)
        jdbcTemplate.update(
            """
            INSERT INTO games (bgg_id, source, name_en) VALUES (13, 'BGG', 'Catan')
            """.trimIndent(),
        )

        val json = """
            {
              "games": [{"id": 1, "bggId": 13, "name": "Catan (다른표기)"}],
              "players": [{"id": 1, "name": "철수"}],
              "plays": [{"uuid": "u1", "gameRefId": 1, "playDate": "2026-07-01 10:00:00", "ignored": false,
                         "playerScores": [{"playerRefId": 1, "score": 10, "winner": true}]}]
            }
        """.trimIndent()

        val submitBody = submitFile(accessToken, json)
        val jobId: Int = JsonPath.read(submitBody, "$.jobId")

        val doneBody = waitUntilDone(accessToken, jobId)
        assertThat(JsonPath.read<Int>(doneBody, "$.summary.gamesMatched")).isEqualTo(1)
        assertThat(JsonPath.read<Int>(doneBody, "$.summary.playsImported")).isEqualTo(1)
    }

    @Test
    fun `무시된 play는 임포트되지 않는다`() {
        val accessToken = issueAccessToken(kakaoId = 2003)
        registerGame(accessToken, "픽셔너리")

        val json = """
            {
              "games": [{"id": 1, "bggId": 0, "name": "픽셔너리"}],
              "players": [],
              "plays": [{"uuid": "u1", "gameRefId": 1, "playDate": "2026-07-01 10:00:00", "ignored": true, "playerScores": []}]
            }
        """.trimIndent()

        val submitBody = submitFile(accessToken, json)
        val jobId: Int = JsonPath.read(submitBody, "$.jobId")

        val doneBody = waitUntilDone(accessToken, jobId)
        assertThat(JsonPath.read<Int>(doneBody, "$.summary.playsImported")).isEqualTo(0)
    }

    @Test
    fun `매칭되지 않은 게임은 unmatched로 분류되고 resolve로 마저 가져온다`() {
        val accessToken = issueAccessToken(kakaoId = 2004)

        val json = """
            {
              "games": [{"id": 1, "bggId": 0, "name": "미등록게임"}],
              "players": [{"id": 1, "name": "철수"}],
              "plays": [{"uuid": "u1", "gameRefId": 1, "playDate": "2026-07-01 10:00:00", "ignored": false,
                         "playerScores": [{"playerRefId": 1, "score": 10, "winner": true}]}]
            }
        """.trimIndent()

        val submitBody = submitFile(accessToken, json)
        val jobId: Int = JsonPath.read(submitBody, "$.jobId")
        val doneBody = waitUntilDone(accessToken, jobId)

        assertThat(JsonPath.read<Int>(doneBody, "$.summary.playsImported")).isEqualTo(0)
        assertThat(JsonPath.read<String>(doneBody, "$.summary.unmatched[0].name")).isEqualTo("미등록게임")

        val newGameId = registerGame(accessToken, "새로등록한게임")
        val resolveBody = mockMvc.perform(
            post("/api/v1/imports/$jobId/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $accessToken")
                .content("""{"resolutions": [{"unmatchedName": "미등록게임", "gameId": $newGameId}]}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString

        assertThat(JsonPath.read<Int>(resolveBody, "$.summary.playsImported")).isEqualTo(1)
        assertThat(JsonPath.read<Int>(resolveBody, "$.summary.gamesMatched")).isEqualTo(1)
        assertThat(JsonPath.read<List<*>>(resolveBody, "$.summary.unmatched")).isEmpty()
    }

    @Test
    fun `이미 진행 중인 잡이 있으면 409를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2005)
        val userId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE provider_id = '2005'",
            Long::class.java,
        )
        jdbcTemplate.update(
            "INSERT INTO import_jobs (user_id, source, status) VALUES (?, 'BGSTATS', 'PENDING')",
            userId,
        )

        mockMvc.perform(
            multipart("/api/v1/imports")
                .file(MockMultipartFile("file", "export.json", "application/json", """{"games":[],"players":[],"plays":[{"uuid":"u","gameRefId":1,"ignored":false,"playerScores":[]}]}""".toByteArray()))
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("IMPORT_ALREADY_RUNNING"))
    }

    @Test
    fun `JSON이 아닌 파일을 올리면 422를 반환한다`() {
        val accessToken = issueAccessToken(kakaoId = 2006)

        mockMvc.perform(
            multipart("/api/v1/imports")
                .file(MockMultipartFile("file", "export.txt", "text/plain", "not json".toByteArray()))
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_FORMAT"))
    }

    @Test
    fun `타 유저의 임포트 잡을 조회하면 404를 반환한다`() {
        val accessTokenA = issueAccessToken(kakaoId = 2007, nickname = "userA")
        registerGame(accessTokenA, "픽토크라시")
        val submitBody = submitFile(
            accessTokenA,
            """{"games":[],"players":[],"plays":[{"uuid":"u","gameRefId":1,"ignored":false,"playerScores":[]}]}""",
        )
        val jobId: Int = JsonPath.read(submitBody, "$.jobId")

        val accessTokenB = issueAccessToken(kakaoId = 2008, nickname = "userB")
        mockMvc.perform(
            get("/api/v1/imports/$jobId")
                .header("Authorization", "Bearer $accessTokenB"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("IMPORT_JOB_NOT_FOUND"))
    }
}
