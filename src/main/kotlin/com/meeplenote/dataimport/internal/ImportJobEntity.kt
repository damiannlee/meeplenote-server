package com.meeplenote.dataimport.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

enum class ImportSource {
    BGSTATS,
    BGG,
}

enum class ImportStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
}

@Entity
@Table(name = "import_jobs")
class ImportJobEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    val source: ImportSource,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: ImportStatus = ImportStatus.PENDING,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    var rawPayload: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary", columnDefinition = "jsonb")
    var resultSummary: String? = null,
    @Column(name = "error_message")
    var errorMessage: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
}
