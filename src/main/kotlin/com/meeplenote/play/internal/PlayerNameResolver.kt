package com.meeplenote.play.internal

import org.springframework.stereotype.Component

@Component
class PlayerNameResolver(
    private val playerRepository: PlayerRepository,
) {

    fun resolveOrCreateByName(userId: Long, names: List<String>): Map<String, PlayerEntity> {
        if (names.isEmpty()) return emptyMap()
        val distinctNames = names.distinct()
        val existing = playerRepository.findAllByUserIdAndNameIn(userId, distinctNames).associateBy { it.name }
        val missingNames = distinctNames.filterNot { existing.containsKey(it) }
        val created = if (missingNames.isEmpty()) {
            emptyList()
        } else {
            playerRepository.saveAll(missingNames.map { PlayerEntity(userId = userId, name = it) })
        }
        return existing + created.associateBy { it.name }
    }
}
