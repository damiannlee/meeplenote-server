package com.meeplenote.dataimport.internal

import com.meeplenote.common.api.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalDate

class BgStatsFileParserTest {

    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val parser = BgStatsFileParser(objectMapper)

    @Test
    fun `games players plays를 파싱한다`() {
        val json = """
            {
              "games": [{"id": 1, "bggId": 13, "name": "Catan"}],
              "players": [{"id": 1, "name": "철수"}],
              "plays": [{"uuid": "u1", "gameRefId": 1, "playDate": "2026-07-01 10:00:00", "ignored": false,
                         "playerScores": [{"playerRefId": 1, "score": 10, "winner": true}]}]
            }
        """.trimIndent()

        val export = parser.parse(json)

        assertThat(export.games).hasSize(1)
        assertThat(export.games[0].bggId).isEqualTo(13L)
        assertThat(export.plays[0].playerScores[0].score).isEqualTo(10)
    }

    @Test
    fun `알 수 없는 필드는 무시한다`() {
        val json = """
            {
              "games": [{"id": 1, "bggId": 13, "name": "Catan", "extra": {"nested": true}}],
              "players": [],
              "plays": [],
              "locations": [{"id": 1, "name": "우리집"}],
              "tags": []
            }
        """.trimIndent()

        val export = parser.parse(json)

        assertThat(export.games).hasSize(1)
    }

    @Test
    fun `games players plays가 모두 비어있으면 UNSUPPORTED_FILE_FORMAT을 던진다`() {
        assertThrows<BusinessException> { parser.parse("""{"games": [], "players": [], "plays": []}""") }
    }

    @Test
    fun `JSON이 아니면 UNSUPPORTED_FILE_FORMAT을 던진다`() {
        assertThrows<BusinessException> { parser.parse("not a json") }
    }

    @Test
    fun `playDate 형식을 LocalDate로 변환한다`() {
        assertThat(parser.parsePlayDate("2026-07-01 10:00:00")).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun `playDate가 형식에 맞지 않으면 UNSUPPORTED_FILE_FORMAT을 던진다`() {
        assertThrows<BusinessException> { parser.parsePlayDate("2026/07/01") }
    }
}
