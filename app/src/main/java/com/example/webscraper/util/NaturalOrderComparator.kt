package com.example.webscraper.util

/**
 * 파일명을 "자연스러운" 순서로 비교한다. 단순 문자열 정렬은 "1화", "10화", "2화" 순으로
 * 섞여버리므로, 숫자로 된 부분은 숫자 크기로, 그 외 부분은 문자열로 비교해
 * "1화", "2화", ..., "10화" 순서가 되도록 한다.
 */
object NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]

            if (ca.isDigit() && cb.isDigit()) {
                val (numA, nextI) = readNumber(a, i)
                val (numB, nextJ) = readNumber(b, j)
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
                i = nextI
                j = nextJ
            } else {
                if (ca != cb) return ca.compareTo(cb)
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    /** [start] 위치부터 이어지는 숫자를 읽어 (값, 다음 인덱스)를 반환한다. 너무 긴 숫자는 자릿수 비교로도 처리한다. */
    private fun readNumber(s: String, start: Int): Pair<Long, Int> {
        var end = start
        while (end < s.length && s[end].isDigit()) end++
        // 자릿수가 매우 많은(Long 범위를 넘는) 숫자는 흔치 않으므로 잘라서 안전하게 변환한다.
        val digits = s.substring(start, end).trimStart('0').ifEmpty { "0" }
        val value = digits.take(18).toLongOrNull() ?: Long.MAX_VALUE
        return value to end
    }
}
