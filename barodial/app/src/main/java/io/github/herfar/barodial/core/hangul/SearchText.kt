package io.github.herfar.barodial.core.hangul

/**
 * 검색용으로 정규화한 문자열.
 *
 * 공백과 기호를 제거하고 소문자로 바꾼다. 원래 문자열에서의 위치를 기억해
 * 검색어 일치 구간을 화면에 강조할 수 있게 한다.
 * 예) "(주) 한국 전력" → "주한국전력"
 */
class SearchText(val original: String) {
    val normalized: String
    private val originalIndex: IntArray
    private val wordStart: BooleanArray

    init {
        val sb = StringBuilder(original.length)
        val indices = IntArray(original.length)
        val starts = BooleanArray(original.length)
        var previousWasSeparator = true
        for ((i, c) in original.withIndex()) {
            if (isSearchable(c)) {
                indices[sb.length] = i
                starts[sb.length] = previousWasSeparator
                sb.append(c.lowercaseChar())
                previousWasSeparator = false
            } else {
                previousWasSeparator = true
            }
        }
        normalized = sb.toString()
        originalIndex = indices.copyOf(normalized.length)
        wordStart = starts.copyOf(normalized.length)
    }

    val isEmpty: Boolean get() = normalized.isEmpty()

    fun isWordStart(normalizedIndex: Int): Boolean = wordStart.getOrElse(normalizedIndex) { false }

    /** 정규화 문자열 구간 [start, end) 를 원래 문자열 구간(inclusive)으로 변환한다. */
    fun toOriginalRange(start: Int, endExclusive: Int): IntRange? {
        if (start < 0 || endExclusive <= start || endExclusive > normalized.length) return null
        return originalIndex[start]..originalIndex[endExclusive - 1]
    }

    companion object {
        fun isSearchable(c: Char): Boolean = c.isLetterOrDigit()

        fun normalize(text: CharSequence): String = buildString(text.length) {
            for (c in text) if (isSearchable(c)) append(c.lowercaseChar())
        }
    }
}
