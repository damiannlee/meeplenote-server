package com.meeplenote.dataimport.internal

import org.springframework.data.jpa.repository.JpaRepository

interface ImportJobRepository : JpaRepository<ImportJobEntity, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): ImportJobEntity?
}
