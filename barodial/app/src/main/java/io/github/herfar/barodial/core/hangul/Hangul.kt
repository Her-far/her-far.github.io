package io.github.herfar.barodial.core.hangul

/**
 * 한글 음절 분해/조합 유틸리티 (유니코드 한글 음절 U+AC00..U+D7A3 기준).
 *
 * 음절 코드 = 0xAC00 + (초성 × 21 + 중성) × 28 + 종성
 */
object Hangul {
    private const val BASE = 0xAC00
    private const val LAST = 0xD7A3
    private const val JUNG_COUNT = 21
    private const val JONG_COUNT = 28
    private const val PER_CHO = JUNG_COUNT * JONG_COUNT

    /** 초성 19자 (호환 자모). 인덱스는 유니코드 초성 순서와 같다. */
    val CHOSEONG: CharArray = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    private val CHOSEONG_INDEX: Map<Char, Int> = CHOSEONG.withIndex().associate { (i, c) -> c to i }

    /**
     * 종성을 다음 음절의 초성으로 넘겼을 때: (남는 종성 인덱스, 넘어가는 자음).
     * 예) "철" 입력 중 다음 모음이 오면 "처" + "ㄹ..." 이 된다.
     */
    private val JONG_SPLIT: Array<Pair<Int, Char>?> = arrayOf(
        null, // 0: 받침 없음
        0 to 'ㄱ', // ㄱ
        0 to 'ㄲ', // ㄲ
        1 to 'ㅅ', // ㄳ
        0 to 'ㄴ', // ㄴ
        4 to 'ㅈ', // ㄵ
        4 to 'ㅎ', // ㄶ
        0 to 'ㄷ', // ㄷ
        0 to 'ㄹ', // ㄹ
        8 to 'ㄱ', // ㄺ
        8 to 'ㅁ', // ㄻ
        8 to 'ㅂ', // ㄼ
        8 to 'ㅅ', // ㄽ
        8 to 'ㅌ', // ㄾ
        8 to 'ㅍ', // ㄿ
        8 to 'ㅎ', // ㅀ
        0 to 'ㅁ', // ㅁ
        0 to 'ㅂ', // ㅂ
        17 to 'ㅅ', // ㅄ
        0 to 'ㅅ', // ㅅ
        0 to 'ㅆ', // ㅆ
        0 to 'ㅇ', // ㅇ
        0 to 'ㅈ', // ㅈ
        0 to 'ㅊ', // ㅊ
        0 to 'ㅋ', // ㅋ
        0 to 'ㅌ', // ㅌ
        0 to 'ㅍ', // ㅍ
        0 to 'ㅎ', // ㅎ
    )

    /** 겹받침 → 첫 자음 받침. 예) ㄺ → ㄹ ("달" 입력 중 "닭"과 일치) */
    private val JONG_FIRST: Map<Int, Int> = mapOf(
        3 to 1, 5 to 4, 6 to 4, 9 to 8, 10 to 8, 11 to 8,
        12 to 8, 13 to 8, 14 to 8, 15 to 8, 18 to 17,
    )

    /** 이중모음 → 첫 모음. 예) ㅘ → ㅗ ("오" 입력 중 "와"와 일치) */
    private val JUNG_FIRST: Map<Int, Int> = mapOf(
        9 to 8, 10 to 8, 11 to 8, 14 to 13, 15 to 13, 16 to 13, 19 to 18,
    )

    /** 색인용 대표 초성 (쌍자음은 기본 자음으로 묶는다). */
    private val BASE_CHOSEONG: Map<Char, Char> = mapOf(
        'ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ',
    )

    fun isSyllable(c: Char): Boolean = c.code in BASE..LAST

    fun isChoseong(c: Char): Boolean = c in CHOSEONG_INDEX

    fun isHangul(c: Char): Boolean = isSyllable(c) || c.code in 0x3131..0x318E

    fun choIndex(c: Char): Int = (c.code - BASE) / PER_CHO

    fun jungIndex(c: Char): Int = ((c.code - BASE) % PER_CHO) / JONG_COUNT

    fun jongIndex(c: Char): Int = (c.code - BASE) % JONG_COUNT

    fun compose(cho: Int, jung: Int, jong: Int): Char =
        (BASE + cho * PER_CHO + jung * JONG_COUNT + jong).toChar()

    /** 음절이면 초성(호환 자모)을, 아니면 그대로 반환한다. */
    fun choseongOf(c: Char): Char = if (isSyllable(c)) CHOSEONG[choIndex(c)] else c

    /** "김철수" → "ㄱㅊㅅ". 한글 음절이 아닌 문자는 그대로 둔다. */
    fun toChoseong(text: CharSequence): String = buildString(text.length) {
        for (c in text) append(choseongOf(c))
    }

    /** 색인 라벨용 대표 초성. 예) '까' → 'ㄱ', 'ㅆ' → 'ㅅ' */
    fun baseChoseongOf(c: Char): Char? {
        val cho = when {
            isSyllable(c) -> CHOSEONG[choIndex(c)]
            isChoseong(c) -> c
            else -> return null
        }
        return BASE_CHOSEONG[cho] ?: cho
    }

    /** 질의가 전부 초성 자음인지. 예) "ㄱㅊㅅ" */
    fun isAllChoseong(text: CharSequence): Boolean =
        text.isNotEmpty() && text.all { isChoseong(it) }

    /**
     * 받침을 다음 음절 초성으로 넘긴 형태. 예) '철' → ('처', 'ㄹ'), '닭' → ('달', 'ㄱ')
     * 받침이 없으면 null.
     */
    fun splitFinal(c: Char): Pair<Char, Char>? {
        if (!isSyllable(c)) return null
        val split = JONG_SPLIT[jongIndex(c)] ?: return null
        return compose(choIndex(c), jungIndex(c), split.first) to split.second
    }

    /**
     * [query] 음절이 아직 조합 중일 때 [target] 음절의 앞부분이 될 수 있는지.
     * 예) '처' → '철', '오' → '왕', '달' → '닭'
     */
    fun isPartialOf(query: Char, target: Char): Boolean {
        if (query == target) return true
        if (!isSyllable(query) || !isSyllable(target)) return false
        if (choIndex(query) != choIndex(target)) return false
        val qJung = jungIndex(query)
        val tJung = jungIndex(target)
        val qJong = jongIndex(query)
        if (qJong == 0) {
            return qJung == tJung || JUNG_FIRST[tJung] == qJung
        }
        if (qJung != tJung) return false
        return JONG_FIRST[jongIndex(target)] == qJong
    }
}
