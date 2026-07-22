package com.meeplenote.play.internal

import com.meeplenote.auth.api.CurrentUserProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class SetFavoriteRequest(
    val isFavorite: Boolean,
)

data class CreatePlayerGroupRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,
)

@Validated
@RestController
@RequestMapping("/api/v1/players")
class PlayerController(
    private val playerService: PlayerService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @GetMapping
    fun list(): List<PlayerSummaryResponse> =
        playerService.listPlayers(currentUserProvider.currentUserId())

    @GetMapping("/recent")
    fun recent(
        @RequestParam(defaultValue = "5") @Min(1) @Max(20) limit: Int,
    ): List<PlayerSummaryResponse> =
        playerService.listRecentlyPlayedWith(currentUserProvider.currentUserId(), limit)

    @PatchMapping("/{playerId}/favorite")
    fun setFavorite(
        @PathVariable playerId: Long,
        @Valid @RequestBody request: SetFavoriteRequest,
    ): PlayerSummaryResponse =
        playerService.setFavorite(currentUserProvider.currentUserId(), playerId, request.isFavorite)
}

@Validated
@RestController
@RequestMapping("/api/v1/player-groups")
class PlayerGroupController(
    private val playerService: PlayerService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreatePlayerGroupRequest): ResponseEntity<PlayerGroupResponse> {
        val response = playerService.createGroup(currentUserProvider.currentUserId(), request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun list(): List<PlayerGroupResponse> =
        playerService.listGroups(currentUserProvider.currentUserId())

    @PutMapping("/{groupId}/players/{playerId}")
    fun addPlayer(
        @PathVariable groupId: Long,
        @PathVariable playerId: Long,
    ): PlayerGroupResponse =
        playerService.addPlayerToGroup(currentUserProvider.currentUserId(), groupId, playerId)

    @DeleteMapping("/{groupId}/players/{playerId}")
    fun removePlayer(
        @PathVariable groupId: Long,
        @PathVariable playerId: Long,
    ): ResponseEntity<Void> {
        playerService.removePlayerFromGroup(currentUserProvider.currentUserId(), groupId, playerId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{groupId}")
    fun delete(@PathVariable groupId: Long): ResponseEntity<Void> {
        playerService.deleteGroup(currentUserProvider.currentUserId(), groupId)
        return ResponseEntity.noContent().build()
    }
}
