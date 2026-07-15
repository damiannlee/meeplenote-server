package com.meeplenote.dataimport.internal

import com.meeplenote.common.api.BusinessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

data class ImportJobResponse(
    val jobId: Long,
    val status: String,
    val summary: ImportSummary?,
    val errorMessage: String?,
) {
    companion object {
        fun of(job: ImportJobEntity, objectMapper: ObjectMapper) = ImportJobResponse(
            jobId = job.id,
            status = job.status.name.lowercase(),
            summary = job.resultSummary?.let { objectMapper.readValue(it, ImportSummary::class.java) },
            errorMessage = job.errorMessage,
        )
    }
}

data class ResolveRequest(
    val resolutions: List<Resolution>,
) {
    data class Resolution(val unmatchedName: String, val gameId: Long)
}

@Service
class ImportService(
    private val importJobRepository: ImportJobRepository,
    private val bgStatsFileParser: BgStatsFileParser,
    private val bgStatsImportProcessor: BgStatsImportProcessor,
    private val objectMapper: ObjectMapper,
) {

    // Intentionally not @Transactional: createJob() must commit before the @Async
    // processor's own thread tries to read the row, or it'd race an uncommitted transaction.
    fun submit(userId: Long, rawJson: String): ImportJobResponse {
        bgStatsFileParser.parse(rawJson)
        val job = createJob(userId, rawJson)
        bgStatsImportProcessor.process(job.id)
        return ImportJobResponse.of(job, objectMapper)
    }

    private fun createJob(userId: Long, rawJson: String): ImportJobEntity =
        try {
            importJobRepository.saveAndFlush(ImportJobEntity(userId = userId, source = ImportSource.BGSTATS, rawPayload = rawJson))
        } catch (ex: DataIntegrityViolationException) {
            throw BusinessException("IMPORT_ALREADY_RUNNING", "이미 진행 중인 임포트가 있습니다", HttpStatus.CONFLICT)
        }

    @Transactional(readOnly = true)
    fun getJob(userId: Long, jobId: Long): ImportJobResponse =
        ImportJobResponse.of(findOwnedJob(userId, jobId), objectMapper)

    @Transactional
    fun resolve(userId: Long, jobId: Long, request: ResolveRequest): ImportJobResponse {
        val job = findOwnedJob(userId, jobId)
        if (job.status != ImportStatus.DONE) {
            throw BusinessException("IMPORT_NOT_RESOLVABLE", "완료된 임포트만 매칭을 해결할 수 있습니다", HttpStatus.CONFLICT)
        }
        val existingSummary = requireNotNull(job.resultSummary) { "done job must have a summary" }
            .let { objectMapper.readValue(it, ImportSummary::class.java) }

        val nameOverrides = request.resolutions.associate { it.unmatchedName to it.gameId }
        val updatedSummary = bgStatsImportProcessor.resolve(job, existingSummary, nameOverrides)
        job.resultSummary = objectMapper.writeValueAsString(updatedSummary)
        importJobRepository.save(job)
        return ImportJobResponse.of(job, objectMapper)
    }

    private fun findOwnedJob(userId: Long, jobId: Long): ImportJobEntity =
        importJobRepository.findByIdAndUserId(jobId, userId)
            ?: throw BusinessException("IMPORT_JOB_NOT_FOUND", "존재하지 않는 임포트 작업입니다", HttpStatus.NOT_FOUND)
}
