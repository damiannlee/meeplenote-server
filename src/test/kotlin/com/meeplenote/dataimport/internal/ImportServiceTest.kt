package com.meeplenote.dataimport.internal

import com.meeplenote.common.api.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class ImportServiceTest {

    private val importJobRepository = mock<ImportJobRepository>()
    private val bgStatsFileParser = mock<BgStatsFileParser>()
    private val bgStatsImportProcessor = mock<BgStatsImportProcessor>()
    private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private val service = ImportService(importJobRepository, bgStatsFileParser, bgStatsImportProcessor, objectMapper)

    private val userId = 1L
    private val rawJson = """{"games":[],"players":[],"plays":[]}"""

    @Test
    fun `유효한 파일이면 잡을 생성하고 비동기 처리를 트리거한다`() {
        whenever(bgStatsFileParser.parse(rawJson)).thenReturn(BgStatsExport())
        val saved = ImportJobEntity(userId = userId, source = ImportSource.BGSTATS, rawPayload = rawJson)
        whenever(importJobRepository.saveAndFlush(any())).thenReturn(saved)

        val response = service.submit(userId, rawJson)

        assertThat(response.status).isEqualTo("pending")
        verify(bgStatsImportProcessor).process(saved.id)
    }

    @Test
    fun `파일 형식이 잘못되면 잡을 생성하지 않고 예외를 던진다`() {
        whenever(bgStatsFileParser.parse(rawJson))
            .thenThrow(BusinessException("UNSUPPORTED_FILE_FORMAT", "지원하지 않는 파일 형식입니다", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY))

        assertThrows<BusinessException> { service.submit(userId, rawJson) }

        verify(importJobRepository, never()).saveAndFlush(any())
    }

    @Test
    fun `이미 진행 중인 잡이 있으면 IMPORT_ALREADY_RUNNING을 던진다`() {
        whenever(bgStatsFileParser.parse(rawJson)).thenReturn(BgStatsExport())
        whenever(importJobRepository.saveAndFlush(any())).thenThrow(DataIntegrityViolationException("dup"))

        val ex = assertThrows<BusinessException> { service.submit(userId, rawJson) }

        assertThat(ex.code).isEqualTo("IMPORT_ALREADY_RUNNING")
    }

    @Test
    fun `존재하지 않거나 소유하지 않은 잡을 조회하면 IMPORT_JOB_NOT_FOUND를 던진다`() {
        whenever(importJobRepository.findByIdAndUserId(1L, userId)).thenReturn(null)

        val ex = assertThrows<BusinessException> { service.getJob(userId, 1L) }

        assertThat(ex.code).isEqualTo("IMPORT_JOB_NOT_FOUND")
    }

    @Test
    fun `완료되지 않은 잡을 resolve하면 IMPORT_NOT_RESOLVABLE을 던진다`() {
        val job = ImportJobEntity(userId = userId, source = ImportSource.BGSTATS)
        job.status = ImportStatus.RUNNING
        whenever(importJobRepository.findByIdAndUserId(1L, userId)).thenReturn(job)

        val ex = assertThrows<BusinessException> {
            service.resolve(userId, 1L, ResolveRequest(emptyList()))
        }

        assertThat(ex.code).isEqualTo("IMPORT_NOT_RESOLVABLE")
    }

    @Test
    fun `완료된 잡을 resolve하면 갱신된 summary를 저장하고 반환한다`() {
        val job = ImportJobEntity(userId = userId, source = ImportSource.BGSTATS)
        job.status = ImportStatus.DONE
        job.resultSummary = objectMapper.writeValueAsString(
            ImportSummary(playsImported = 5, gamesMatched = 2, unmatched = listOf(UnmatchedGame("커스텀게임", emptyList()))),
        )
        whenever(importJobRepository.findByIdAndUserId(1L, userId)).thenReturn(job)
        val updatedSummary = ImportSummary(playsImported = 8, gamesMatched = 3, unmatched = emptyList())
        whenever(bgStatsImportProcessor.resolve(any(), any(), any())).thenReturn(updatedSummary)

        val response = service.resolve(userId, 1L, ResolveRequest(listOf(ResolveRequest.Resolution("커스텀게임", 42L))))

        assertThat(response.summary).isEqualTo(updatedSummary)
        val jobCaptor = argumentCaptor<ImportJobEntity>()
        verify(importJobRepository).save(jobCaptor.capture())
        assertThat(jobCaptor.firstValue.resultSummary).isEqualTo(objectMapper.writeValueAsString(updatedSummary))
    }
}
