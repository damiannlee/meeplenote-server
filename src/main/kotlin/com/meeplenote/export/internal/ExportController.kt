package com.meeplenote.export.internal

import com.meeplenote.auth.api.CurrentUserProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/exports")
class ExportController(
    private val exportService: ExportService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @GetMapping
    fun exportAll(): ResponseEntity<ExportResponse> {
        val response = exportService.exportAll(currentUserProvider.currentUserId())
        return ResponseEntity.ok(response)
    }
}
