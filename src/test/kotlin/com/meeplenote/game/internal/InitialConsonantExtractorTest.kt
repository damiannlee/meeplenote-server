package com.meeplenote.game.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InitialConsonantExtractorTest {

    @Test
    fun `한글 음절은 초성으로 치환된다`() {
        assertThat(InitialConsonantExtractor.extract("카탄")).isEqualTo("ㅋㅌ")
    }

    @Test
    fun `영문은 그대로 유지된다`() {
        assertThat(InitialConsonantExtractor.extract("Catan")).isEqualTo("Catan")
    }

    @Test
    fun `한글과 영문이 섞인 이름은 한글 부분만 치환된다`() {
        assertThat(InitialConsonantExtractor.extract("카탄Catan")).isEqualTo("ㅋㅌCatan")
    }

    @Test
    fun `초성 자음으로만 구성된 검색어는 초성 전용으로 판별한다`() {
        assertThat(InitialConsonantExtractor.isInitialsOnly("ㅋㅌ")).isTrue()
    }

    @Test
    fun `완성된 한글 음절은 초성 전용이 아니다`() {
        assertThat(InitialConsonantExtractor.isInitialsOnly("카탄")).isFalse()
    }

    @Test
    fun `영문 검색어는 초성 전용이 아니다`() {
        assertThat(InitialConsonantExtractor.isInitialsOnly("Catan")).isFalse()
    }

    @Test
    fun `빈 문자열은 초성 전용이 아니다`() {
        assertThat(InitialConsonantExtractor.isInitialsOnly("")).isFalse()
    }
}
