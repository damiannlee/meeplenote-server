package com.meeplenote.dataimport.internal

import com.meeplenote.auth.api.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/v1/imports")
class ImportController(
    private val importService: ImportService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping
    fun submit(@RequestParam file: MultipartFile): ResponseEntity<ImportJobResponse> {
        val rawJson = String(file.bytes, StandardCharsets.UTF_8)
        val response = importService.submit(currentUserProvider.currentUserId(), rawJson)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    @GetMapping("/{jobId}")
    fun get(@PathVariable jobId: Long): ResponseEntity<ImportJobResponse> {
        val response = importService.getJob(currentUserProvider.currentUserId(), jobId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{jobId}/resolve")
    fun resolve(@PathVariable jobId: Long, @RequestBody request: ResolveRequest): ResponseEntity<ImportJobResponse> {
        val response = importService.resolve(currentUserProvider.currentUserId(), jobId, request)
        return ResponseEntity.ok(response)
    }
}
