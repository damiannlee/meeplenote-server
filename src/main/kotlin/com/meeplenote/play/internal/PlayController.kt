package com.meeplenote.play.internal

import com.meeplenote.auth.api.CurrentUserProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

data class PlayerInput(
    val playerId: Long? = null,
    @field:Size(max = 50)
    val name: String? = null,
    val score: Int? = null,
    val isWinner: Boolean = false,
) {
    @get:AssertTrue(message = "playerId 또는 name 중 하나는 필요합니다")
    val isValidInput: Boolean
        get() = playerId != null || !name.isNullOrBlank()
}

data class CreatePlayRequest(
    @field:NotNull
    val gameId: Long,
    val playedAt: LocalDate? = null,
    @field:Valid
    val players: List<PlayerInput> = emptyList(),
    @field:Size(max = 2000)
    val note: String? = null,
    @field:Min(1)
    @field:Max(5)
    val rating: Int? = null,
    @field:Size(max = 300)
    val photoKey: String? = null,
)

@Validated
@RestController
@RequestMapping("/api/v1/plays")
class PlayController(
    private val playService: PlayService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping
    fun create(
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @Valid @RequestBody request: CreatePlayRequest,
    ): ResponseEntity<PlayResponse> {
        val response = playService.recordPlay(currentUserProvider.currentUserId(), idempotencyKey, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) limit: Int,
    ): PlayListResponse =
        playService.listPlays(currentUserProvider.currentUserId(), cursor, limit)
}
