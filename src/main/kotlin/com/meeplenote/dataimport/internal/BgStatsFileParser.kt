package com.meeplenote.dataimport.internal

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.meeplenote.common.api.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@JsonIgnoreProperties(ignoreUnknown = true)
data class BgStatsGame(
    val id: Long,
    val bggId: Long? = null,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BgStatsPlayer(
    val id: Long,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BgStatsPlayerScore(
    val playerRefId: Long,
    val score: Int? = null,
    val winner: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BgStatsPlay(
    val uuid: String? = null,
    val gameRefId: Long,
    val playDate: String? = null,
    val ignored: Boolean = false,
    val playerScores: List<BgStatsPlayerScore> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BgStatsExport(
    val games: List<BgStatsGame> = emptyList(),
    val players: List<BgStatsPlayer> = emptyList(),
    val plays: List<BgStatsPlay> = emptyList(),
)

@Component
class BgStatsFileParser(
    private val objectMapper: ObjectMapper,
) {

    private val playDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parse(rawJson: String): BgStatsExport {
        val export = try {
            objectMapper.readValue(rawJson, BgStatsExport::class.java)
        } catch (ex: JacksonException) {
            throw unsupportedFormat()
        }
        if (export.games.isEmpty() && export.players.isEmpty() && export.plays.isEmpty()) {
            throw unsupportedFormat()
        }
        return export
    }

    fun parsePlayDate(playDate: String?): LocalDate {
        val raw = playDate ?: throw unsupportedFormat()
        return try {
            LocalDate.parse(raw, playDateFormatter)
        } catch (ex: java.time.format.DateTimeParseException) {
            throw unsupportedFormat()
        }
    }

    private fun unsupportedFormat() =
        BusinessException("UNSUPPORTED_FILE_FORMAT", "지원하지 않는 파일 형식입니다", HttpStatus.UNPROCESSABLE_ENTITY)
}
