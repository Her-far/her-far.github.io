package io.github.herfar.barodial.data.search

import io.github.herfar.barodial.core.hangul.Hangul
import io.github.herfar.barodial.core.hangul.HangulMatcher
import io.github.herfar.barodial.core.hangul.SearchText
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.PhoneNumber

enum class MatchField { NAME, NICKNAME, ORGANIZATION, PHONE }

/**
 * 검색 결과 하나.
 * @param nameHighlight displayName 에서 일치한 구간 (inclusive)
 * @param phoneDigitRange matchedPhone.nationalDigits 에서 일치한 구간 [start, end)
 * @param matchedText 이름 외 필드(회사·별명)에서 일치했을 때의 원문
 */
data class SearchResult(
    val contact: Contact,
    val score: Int,
    val field: MatchField,
    val nameHighlight: IntRange? = null,
    val matchedPhone: PhoneNumber? = null,
    val phoneDigitRange: IntRange? = null,
    val matchedText: String? = null,
    val matchedTextHighlight: IntRange? = null,
)

/** 검색을 빠르게 하기 위해 연락처마다 미리 정규화해 둔 색인. */
class SearchIndex internal constructor(internal val entries: List<IndexedContact>) {
    val size: Int get() = entries.size

    internal class IndexedContact(
        val contact: Contact,
        val order: Int,
        val name: SearchText,
        val nicknames: List<SearchText>,
        val organization: SearchText?,
        val phones: List<IndexedPhone>,
    )

    internal class IndexedPhone(val phone: PhoneNumber, val rawDigits: String)

    companion object {
        val EMPTY = SearchIndex(emptyList())
    }
}

/**
 * 연락처 통합 검색 엔진 (순수 Kotlin, Android 의존성 없음).
 *
 * 검색 대상: 이름, 이름 초성, 별명, 회사명, 모든 전화번호(하이픈·국가번호 무시).
 * 점수: 정확히 일치 > 앞부분 일치 > 단어 시작 일치 > 중간 일치, 이름 > 별명 > 회사.
 * 같은 점수면 [boost](즐겨찾기·발신 횟수) → 가나다순.
 */
class ContactSearchEngine {

    /** [contacts] 는 이미 가나다순으로 정렬되어 있다고 가정한다 (동점 시 순서 유지). */
    fun buildIndex(contacts: List<Contact>): SearchIndex = SearchIndex(
        contacts.mapIndexed { i, c ->
            SearchIndex.IndexedContact(
                contact = c,
                order = i,
                name = SearchText(c.displayName),
                nicknames = c.nicknames.filter { it.isNotBlank() }.map { SearchText(it) },
                organization = listOfNotNull(c.organization, c.jobTitle)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .takeIf { it.isNotEmpty() }
                    ?.let { SearchText(it) },
                phones = c.phones.map { SearchIndex.IndexedPhone(it, KoreanPhoneNumbers.digitsOnly(it.number)) },
            )
        },
    )

    fun search(
        index: SearchIndex,
        rawQuery: String,
        limit: Int = 200,
        boost: (Contact) -> Int = { 0 },
    ): List<SearchResult> {
        val query = Query.parse(rawQuery) ?: return emptyList()
        val scored = ArrayList<Pair<SearchResult, Int>>()
        for (entry in index.entries) {
            val result = score(entry, query) ?: continue
            scored += result to boost(entry.contact)
        }
        val orderOf = HashMap<Long, Int>(scored.size * 2)
        for (entry in index.entries) orderOf[entry.contact.id] = entry.order
        scored.sortWith(
            compareByDescending<Pair<SearchResult, Int>> { it.first.score }
                .thenByDescending { it.second }
                .thenBy { orderOf[it.first.contact.id] ?: Int.MAX_VALUE },
        )
        return scored.asSequence().take(limit).map { it.first }.toList()
    }

    /** 전화번호(숫자)로만 검색: 키패드 입력 중 일치 연락처 표시용. */
    fun searchByNumber(index: SearchIndex, digits: String, limit: Int = 50): List<SearchResult> {
        val d = KoreanPhoneNumbers.toNationalDigits(digits)
        if (d.isEmpty()) return emptyList()
        return index.entries.asSequence()
            .mapNotNull { e -> bestPhoneMatch(e, d)?.let { e to it } }
            .sortedWith(compareByDescending<Pair<SearchIndex.IndexedContact, PhoneMatch>> { it.second.score }.thenBy { it.first.order })
            .take(limit)
            .map { (e, m) ->
                SearchResult(e.contact, m.score, MatchField.PHONE, matchedPhone = m.phone, phoneDigitRange = m.range)
            }
            .toList()
    }

    private class Query(val text: String, val choseongOnly: Boolean, val digits: String?, val numeric: Boolean) {
        companion object {
            fun parse(raw: String): Query? {
                val text = SearchText.normalize(raw)
                if (text.isEmpty()) return null
                val numeric = KoreanPhoneNumbers.looksLikePhoneQuery(raw.trim())
                val digits = if (numeric) KoreanPhoneNumbers.toNationalDigits(raw).ifEmpty { null } else null
                return Query(text, Hangul.isAllChoseong(text), digits, numeric)
            }
        }
    }

    private class TextMatch(val score: Int, val start: Int, val end: Int)

    private class PhoneMatch(val phone: PhoneNumber, val score: Int, val range: IntRange?)

    private fun score(entry: SearchIndex.IndexedContact, q: Query): SearchResult? {
        var best: SearchResult? = null

        fun offer(candidate: SearchResult) {
            val current = best
            if (current == null || candidate.score > current.score) best = candidate
        }

        if (!q.numeric) {
            matchText(entry.name, q)?.let { m ->
                offer(
                    SearchResult(
                        contact = entry.contact,
                        score = m.score,
                        field = MatchField.NAME,
                        nameHighlight = entry.name.toOriginalRange(m.start, m.end),
                    ),
                )
            }
            for (nick in entry.nicknames) {
                matchText(nick, q)?.let { m ->
                    offer(
                        SearchResult(
                            contact = entry.contact,
                            score = m.score - NICKNAME_PENALTY,
                            field = MatchField.NICKNAME,
                            matchedText = nick.original,
                            matchedTextHighlight = nick.toOriginalRange(m.start, m.end),
                        ),
                    )
                }
            }
            entry.organization?.let { org ->
                matchText(org, q)?.let { m ->
                    offer(
                        SearchResult(
                            contact = entry.contact,
                            score = m.score - ORGANIZATION_PENALTY,
                            field = MatchField.ORGANIZATION,
                            matchedText = org.original,
                            matchedTextHighlight = org.toOriginalRange(m.start, m.end),
                        ),
                    )
                }
            }
        } else {
            // 숫자 검색어: 이름에 숫자가 들어간 연락처(예: "302호 관리실")도 찾는다.
            matchText(entry.name, q)?.let { m ->
                offer(
                    SearchResult(
                        entry.contact,
                        m.score - NUMERIC_NAME_PENALTY,
                        MatchField.NAME,
                        nameHighlight = entry.name.toOriginalRange(m.start, m.end),
                    ),
                )
            }
            val digits = q.digits
            if (digits != null) {
                bestPhoneMatch(entry, digits)?.let { m ->
                    offer(
                        SearchResult(
                            contact = entry.contact,
                            score = m.score,
                            field = MatchField.PHONE,
                            matchedPhone = m.phone,
                            phoneDigitRange = m.range,
                        ),
                    )
                }
            }
        }
        return best
    }

    private fun matchText(field: SearchText, q: Query): TextMatch? {
        val t = field.normalized
        if (t.isEmpty()) return null
        var best: TextMatch? = null
        for (start in t.indices) {
            val end = HangulMatcher.matchAt(q.text, t, start)
            if (end < 0) continue
            val score = when {
                start == 0 && end == t.length -> if (q.choseongOnly) 88 else 100
                start == 0 -> if (q.choseongOnly) 80 else 90
                field.isWordStart(start) -> if (q.choseongOnly) 70 else 78
                else -> if (q.choseongOnly) 55 else 65
            }
            if (best == null || score > best.score) best = TextMatch(score, start, end)
            if (start == 0) break
        }
        return best
    }

    private fun bestPhoneMatch(entry: SearchIndex.IndexedContact, digits: String): PhoneMatch? {
        var best: PhoneMatch? = null
        for (p in entry.phones) {
            val m = matchPhone(p, digits) ?: continue
            if (best == null || m.score > best.score) best = m
        }
        return best
    }

    private fun matchPhone(p: SearchIndex.IndexedPhone, q: String): PhoneMatch? {
        val national = p.phone.nationalDigits
        if (national.isNotEmpty()) {
            when {
                national == q -> return PhoneMatch(p.phone, 100, national.indices)
                national.startsWith(q) -> return PhoneMatch(p.phone, 92, 0 until q.length)
                q.length >= MIN_INFIX_DIGITS && national.endsWith(q) ->
                    return PhoneMatch(p.phone, 82, (national.length - q.length) until national.length)
                q.length >= MIN_INFIX_DIGITS -> {
                    val i = national.indexOf(q)
                    if (i >= 0) return PhoneMatch(p.phone, 72, i until i + q.length)
                }
            }
        }
        // 국제 형식 그대로 저장된 번호 ("+1 650 ...") 도 숫자만으로 찾는다.
        if (p.rawDigits != national && q.length >= MIN_INFIX_DIGITS && p.rawDigits.contains(q)) {
            return PhoneMatch(p.phone, 68, null)
        }
        return null
    }

    companion object {
        /** 이 길이 이상일 때만 번호 중간·끝자리 일치를 허용한다 (1~2자리는 앞자리 일치만). */
        const val MIN_INFIX_DIGITS = 3
        private const val NICKNAME_PENALTY = 6
        private const val ORGANIZATION_PENALTY = 12
        private const val NUMERIC_NAME_PENALTY = 10
    }
}
