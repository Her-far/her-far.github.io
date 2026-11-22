package io.github.herfar.barodial.core.text

import io.github.herfar.barodial.core.hangul.Hangul
import java.text.Collator
import java.util.Locale

/**
 * 연락처 가나다순 정렬과 빠른 스크롤 색인.
 * 순서: 한글(ㄱ~ㅎ) → 영문(A~Z) → 숫자·기호(#)
 */
object KoreanSort {
    const val OTHER_LABEL = "#"

    val HANGUL_LABELS: List<String> =
        listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

    val ALL_LABELS: List<String> = HANGUL_LABELS + ('A'..'Z').map { it.toString() } + OTHER_LABEL

    private val LABEL_ORDER: Map<String, Int> = ALL_LABELS.withIndex().associate { (i, l) -> l to i }

    private fun firstChar(name: String): Char? = name.firstOrNull { !it.isWhitespace() }

    /** 0: 한글, 1: 영문, 2: 그 외 */
    fun group(name: String): Int {
        val c = firstChar(name) ?: return 2
        return when {
            Hangul.isHangul(c) -> 0
            c.uppercaseChar() in 'A'..'Z' -> 1
            else -> 2
        }
    }

    fun indexLabel(name: String): String {
        val c = firstChar(name) ?: return OTHER_LABEL
        Hangul.baseChoseongOf(c)?.let { return it.toString() }
        val upper = c.uppercaseChar()
        if (upper in 'A'..'Z') return upper.toString()
        return OTHER_LABEL
    }

    fun labelOrder(label: String): Int = LABEL_ORDER[label] ?: Int.MAX_VALUE

    /** 이름 비교기. Collator 는 스레드 안전하지 않으므로 호출마다 새로 만든다. */
    fun nameComparator(): Comparator<String> {
        val collator = Collator.getInstance(Locale.KOREAN).apply { strength = Collator.SECONDARY }
        return Comparator { a, b ->
            val ga = group(a)
            val gb = group(b)
            if (ga != gb) return@Comparator ga - gb
            val la = labelOrder(indexLabel(a))
            val lb = labelOrder(indexLabel(b))
            if (la != lb) return@Comparator la - lb
            collator.compare(a.trim(), b.trim())
        }
    }
}
