package com.meeplenote.collection.internal

import com.meeplenote.auth.api.CurrentUserProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class UpsertCollectionRequest(
    @field:NotNull
    val status: CollectionStatus,
)

@RestController
@RequestMapping("/api/v1/collections")
class CollectionController(
    private val collectionService: CollectionService,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PutMapping("/{gameId}")
    fun upsert(
        @PathVariable gameId: Long,
        @Valid @RequestBody request: UpsertCollectionRequest,
    ): ResponseEntity<CollectionResponse> {
        val response = collectionService.upsert(currentUserProvider.currentUserId(), gameId, request.status)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{gameId}")
    fun remove(@PathVariable gameId: Long): ResponseEntity<Void> {
        collectionService.remove(currentUserProvider.currentUserId(), gameId)
        return ResponseEntity.noContent().build()
    }
}
