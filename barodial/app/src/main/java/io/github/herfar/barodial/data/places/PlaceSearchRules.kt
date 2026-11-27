package io.github.herfar.barodial.data.places

import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers

/** 검색창 입력을 온라인 검색어로 바꾼 결과 */
data class OnlineQuery(
    /** 카카오로 보낼 검색어 ('근처' 같은 말은 뺀 것) */
    val keyword: String,
    /** "근처 약국"처럼 가까운 곳을 찾는 검색어였는지 */
    val wantsNearby: Boolean,
)

/**
 * 어떤 입력을 온라인으로 검색할지 정한다. 불필요한 API 호출과 개인정보 전송을 막기 위한 규칙:
 *  - 공백을 뺀 길이가 2글자 미만이면 보내지 않는다.
 *  - 숫자·전화번호 형태(010…, 1588…)는 보내지 않는다. 번호 검색은 기기 안에서만 한다.
 *  - 낱자 자모(ㄱ, ㅊ, ㅏ …)가 들어 있으면 보내지 않는다. 초성 검색이거나 아직 글자를 조합하는 중이다.
 *  - 너무 긴 입력(50자 초과)은 보내지 않는다.
 */
object PlaceSearchRules {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 50

    /** 검색어 입력이 멈춘 뒤 이만큼 기다렸다가 요청한다. */
    const val DEBOUNCE_MILLIS = 350L

    /** (접두어, 뒤에 띄어쓰기가 꼭 있어야 하는지). "가까운나라"처럼 이름의 일부일 수 있는 말은 띄어 쓴 경우만 인정한다. */
    private val NEARBY_PREFIXES = listOf("내 주변" to false, "내주변" to false, "근처" to false, "주변" to false, "가까운" to true)
    private val NEARBY_SUFFIXES = listOf("근처", "주변")
    private val NEARBY_WORDS = setOf("근처", "주변", "내 주변", "내주변", "가까운")

    fun parse(raw: String): OnlineQuery? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.length > MAX_LENGTH || text in NEARBY_WORDS) return null
        if (KoreanPhoneNumbers.looksLikePhoneQuery(text)) return null
        if (text.any { isStandaloneJamo(it) }) return null

        var keyword = text
        var nearby = false
        for ((p, needsSpace) in NEARBY_PREFIXES) {
            val matches = if (needsSpace) keyword.startsWith("$p ") else keyword.startsWith(p)
            if (matches && keyword.length > p.length) {
                keyword = keyword.removePrefix(p).trim()
                nearby = true
                break
            }
        }
        if (!nearby) {
            for (s in NEARBY_SUFFIXES) {
                if (keyword.endsWith(" $s")) {
                    keyword = keyword.removeSuffix(s).trim()
                    nearby = true
                    break
                }
            }
        }
        if (keyword.count { !it.isWhitespace() } < MIN_LENGTH) return null
        return OnlineQuery(keyword, nearby)
    }

    /** 한글 호환 자모(ㄱ-ㅎ, ㅏ-ㅣ)와 첫가끝 자모 */
    private fun isStandaloneJamo(c: Char): Boolean = c in 'ㄱ'..'ㆎ' || c in 'ᄀ'..'ᇿ'
}

/** 화면에 보여 줄 온라인 결과 한 줄 */
data class PlaceItem(
    val place: Place,
    /** 같은 이름의 장소가 여럿일 때 구분용 지역 (예: "경기 김포시") */
    val regionLabel: String?,
    /** 검색어와 이름이 정확히 같다 */
    val exactMatch: Boolean,
)

/**
 * 결과 정렬과 지점 구분.
 *  - 정확도순: 이름이 검색어와 정확히 같은 것 → 검색어로 시작 → 검색어 포함 → 나머지 (같은 등급은 카카오 순서 유지)
 *  - 거리순: 정확히 같은 이름만 맨 앞으로, 나머지는 카카오가 준 거리 순서 유지
 *  - 같은 이름이 여러 개면(지점·지역회·지사) 주소의 시·도와 시·군·구를 붙여 구분한다.
 */
object PlaceRanking {

    fun normalize(s: String): String = buildString {
        for (c in s.lowercase()) if (c.isLetterOrDigit()) append(c)
    }

    fun rank(keyword: String, places: List<Place>, byDistance: Boolean): List<PlaceItem> {
        val q = normalize(keyword)
        fun tier(p: Place): Int {
            val n = normalize(p.name)
            return when {
                q.isEmpty() -> 3
                n == q -> 0
                byDistance -> 3
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                else -> 3
            }
        }
        val sorted = places.withIndex().sortedWith(compareBy({ tier(it.value) }, { it.index })).map { it.value }
        val nameCounts = sorted.groupingBy { normalize(it.name) }.eachCount()
        return sorted.map { p ->
            val duplicate = (nameCounts[normalize(p.name)] ?: 0) > 1
            PlaceItem(
                place = p,
                regionLabel = if (duplicate) regionOf(p) else null,
                exactMatch = q.isNotEmpty() && normalize(p.name) == q,
            )
        }
    }

    /** 주소의 앞 두 단계 (예: "경기 김포시 사우중로 1" → "경기 김포시") */
    fun regionOf(p: Place): String? {
        val tokens = p.displayAddress.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.take(2).joinToString(" ")
    }

    /** 다음 페이지를 붙일 때 같은 장소가 두 번 나오지 않게 한다. */
    fun merge(existing: List<Place>, next: List<Place>): List<Place> {
        val seen = existing.mapTo(HashSet()) { it.id.ifEmpty { it.name + it.displayAddress } }
        return existing + next.filter { seen.add(it.id.ifEmpty { it.name + it.displayAddress }) }
    }
}
