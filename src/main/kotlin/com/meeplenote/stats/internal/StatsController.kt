package com.meeplenote.stats.internal

import com.meeplenote.auth.api.CurrentUserProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/stats")
class StatsController(
    private val statsService: StatsService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @GetMapping("/summary")
    fun summary(): ResponseEntity<StatsSummaryResponse> {
        val response = statsService.getSummary(currentUserProvider.currentUserId())
        return ResponseEntity.ok(response)
    }
}
