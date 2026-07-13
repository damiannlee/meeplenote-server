package com.meeplenote.collection.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CollectionSortConverterTest {

    private val converter = CollectionSortConverter()

    @Test
    fun `snake_case 값을 CollectionSort로 변환한다`() {
        assertThat(converter.convert("name")).isEqualTo(CollectionSort.NAME)
        assertThat(converter.convert("recent_play")).isEqualTo(CollectionSort.RECENT_PLAY)
        assertThat(converter.convert("play_count")).isEqualTo(CollectionSort.PLAY_COUNT)
    }

    @Test
    fun `지원하지 않는 값이면 예외를 던진다`() {
        assertThrows<IllegalArgumentException> { converter.convert("unknown") }
    }
}
