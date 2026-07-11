package com.meeplenote.game.internal

import com.meeplenote.auth.api.CurrentUserProvider
import com.meeplenote.common.api.BusinessException
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CustomGameRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
)

@RestController
@RequestMapping("/api/v1/games")
class GameController(
    private val gameSearchService: GameSearchService,
    private val gameService: GameService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): GameSearchResponse {
        if (q.isEmpty()) {
            throw BusinessException("QUERY_TOO_SHORT", "검색어는 1자 이상이어야 합니다", HttpStatus.BAD_REQUEST)
        }
        return gameSearchService.search(q, limit)
    }

    @PostMapping
    fun registerCustom(@RequestBody @Valid request: CustomGameRequest): ResponseEntity<CustomGameResponse> {
        val response = gameService.registerCustom(currentUserProvider.currentUserId(), request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
