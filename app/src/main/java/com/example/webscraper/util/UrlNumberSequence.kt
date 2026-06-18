package com.example.webscraper.util

/**
 * URL 끝부분이 회차를 나타내는 숫자로 끝나는 사이트에서, 그 숫자만 바꿔서
 * 다른 회차의 URL을 만들어내기 위한 유틸리티. (매크로 기능에서 사용)
 *
 * 예) "https://example.com/novel/12" -> 7화 -> "https://example.com/novel/7"
 *     "https://example.com/novel/007/" -> 8화 -> "https://example.com/novel/008/"
 *     (원래 숫자가 0으로 채워져 있었으면 같은 자릿수를 유지한다.)
 */
object UrlNumberSequence {

    // 끝에 붙은 슬래시(있다면)는 무시하고, 그 앞의 연속된 숫자를 찾는다.
    private val TRAILING_NUMBER_REGEX = Regex("(\\d+)(/?)$")

    /** [url]의 끝에서 회차 숫자를 찾아 반환한다. 찾지 못하면 null. */
    fun extractTrailingNumber(url: String): Int? {
        val digitsRange = findDigitsRange(url) ?: return null
        return url.substring(digitsRange).toIntOrNull()
    }

    /**
     * [url]의 끝 숫자를 [newNumber]로 바꾼 새 URL을 반환한다.
     * 원래 숫자와 자릿수가 같아지도록 0으로 왼쪽을 채운다. 끝에서 숫자를 찾지 못하면 null.
     */
    fun withTrailingNumber(url: String, newNumber: Int): String? {
        if (newNumber < 0) return null
        val digitsRange = findDigitsRange(url) ?: return null
        val originalDigits = url.substring(digitsRange)
        val newDigits = newNumber.toString().padStart(originalDigits.length, '0')
        return url.substring(0, digitsRange.first) + newDigits + url.substring(digitsRange.last + 1)
    }

    private fun findDigitsRange(url: String): IntRange? {
        val match = TRAILING_NUMBER_REGEX.find(url) ?: return null
        return match.groups[1]?.range
    }
}
