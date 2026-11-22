package io.github.herfar.barodial.core.hangul

/**
 * 한국어 검색용 문자열 매칭.
 *
 * - 완성형 음절은 그대로 비교한다. ("철수" ↔ "철수")
 * - 초성 자음은 해당 초성을 가진 음절과 일치한다. ("ㄱㅊㅅ" ↔ "김철수", "김ㅊ" ↔ "김철수")
 * - 질의의 마지막 글자는 키보드에서 아직 조합 중일 수 있으므로 부분 일치를 허용한다.
 *   ("김처" ↔ "김철수", "김철" ↔ "김처리", "와" 입력 중 "오" ↔ "왕")
 * - 라틴 문자는 대소문자를 구분하지 않는다.
 *
 * 질의와 대상은 [SearchText.normalize] 로 미리 정규화(소문자, 공백·기호 제거)되어 있어야 한다.
 */
object HangulMatcher {

    /**
     * [query] 가 [target] 의 [start] 위치부터 일치하면 대상 문자열에서의 끝 위치(exclusive)를,
     * 아니면 -1 을 반환한다.
     */
    fun matchAt(query: CharSequence, target: CharSequence, start: Int): Int {
        if (query.isEmpty()) return start
        var ti = start
        val last = query.length - 1
        for (qi in 0..last) {
            if (ti >= target.length) return -1
            val qc = query[qi]
            val tc = target[ti]
            if (qi < last) {
                if (!charMatches(qc, tc)) return -1
                ti++
            } else {
                if (charMatches(qc, tc) || Hangul.isPartialOf(qc, tc)) return ti + 1
                // "철" 이 실제로는 "처" + "ㄹ…" 일 수 있다 (예: "처리").
                val (head, moved) = Hangul.splitFinal(qc) ?: return -1
                if (ti + 1 < target.length && head == tc && charMatches(moved, target[ti + 1])) {
                    return ti + 2
                }
                return -1
            }
        }
        return ti
    }

    /** 첫 번째로 일치하는 위치. 없으면 -1. */
    fun indexOf(query: CharSequence, target: CharSequence, from: Int = 0): Int {
        if (query.isEmpty()) return 0
        for (start in from until target.length) {
            if (matchAt(query, target, start) >= 0) return start
        }
        return -1
    }

    fun contains(query: CharSequence, target: CharSequence): Boolean = indexOf(query, target) >= 0

    fun charMatches(query: Char, target: Char): Boolean {
        if (query == target) return true
        if (Hangul.isChoseong(query)) {
            return Hangul.isSyllable(target) && Hangul.choseongOf(target) == query
        }
        return query.lowercaseChar() == target.lowercaseChar()
    }
}
