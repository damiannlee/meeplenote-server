package com.meeplenote.collection.internal

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** Maps the documented snake_case query values (?sort=recent_play) onto [CollectionSort]. */
@Component
class CollectionSortConverter : Converter<String, CollectionSort> {

    override fun convert(source: String): CollectionSort =
        when (source) {
            "name" -> CollectionSort.NAME
            "recent_play" -> CollectionSort.RECENT_PLAY
            "play_count" -> CollectionSort.PLAY_COUNT
            else -> throw IllegalArgumentException("Unsupported sort value: $source")
        }
}
