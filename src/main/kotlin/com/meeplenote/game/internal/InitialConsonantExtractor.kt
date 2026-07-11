package com.meeplenote.game.internal

/** Extracts the leading consonant from Hangul syllables. Application-side half of ADR-004's pg_trgm + `name_initials` column. */
object InitialConsonantExtractor {

    private const val HANGUL_BASE = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val MEDIALS_COUNT = 21
    private const val FINALS_COUNT = 28

    private val INITIAL_CONSONANTS = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    /** Replaces Hangul syllables with their leading consonant; other characters pass through unchanged (supports mixed-language name search). */
    fun extract(text: String): String =
        text.map { ch ->
            if (ch.code in HANGUL_BASE..HANGUL_LAST) {
                val syllableIndex = ch.code - HANGUL_BASE
                val initialIndex = syllableIndex / (MEDIALS_COUNT * FINALS_COUNT)
                INITIAL_CONSONANTS[initialIndex]
            } else {
                ch
            }
        }.joinToString("")

    /** True if the query consists solely of leading-consonant jamo — the server then searches the initials column. */
    fun isInitialsOnly(query: String): Boolean =
        query.isNotEmpty() && query.all { it in INITIAL_CONSONANTS }
}
