package io.github.herfar.barodial.data

import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.search.ContactSearchEngine
import io.github.herfar.barodial.data.search.MatchField
import io.github.herfar.barodial.testing.TestContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class ContactSearchEngineTest {

    private val engine = ContactSearchEngine()
    private val ready = ContactsState.Ready.build(ContactsState.Ready.sortContacts(TestContacts.ALL), engine)

    private fun names(query: String): List<String> = engine.search(ready.index, query).map { it.contact.displayName }

    @Test
    fun fullNameIsTopResult() {
        val results = engine.search(ready.index, "김철수")
        assertEquals("김철수", results.first().contact.displayName)
        assertEquals(100, results.first().score)
        assertEquals(0..2, results.first().nameHighlight)
    }

    @Test
    fun choseongSearch() {
        assertEquals(listOf("김철수"), names("ㄱㅊㅅ"))
        assertEquals(listOf("김철민"), names("ㄱㅊㅁ"))
        // 초성 일부: ㄱㅊ → 김철민, 김철수 (가나다순)
        assertEquals(listOf("김철민", "김철수"), names("ㄱㅊ"))
    }

    @Test
    fun partialNameSearch() {
        val result = names("철수")
        assertTrue(result.containsAll(listOf("김철수", "이철수")))
        assertFalse("김철민" in result)
    }

    @Test
    fun mixedSyllableAndChoseong() {
        assertEquals(listOf("김철수"), names("김ㅊㅅ"))
    }

    @Test
    fun imeCompositionShowsResultsWhileTyping() {
        // "김철수"를 치는 도중 "김처" 상태에서도 결과가 보여야 한다.
        assertTrue(names("김처").containsAll(listOf("김철수", "김철민")))
        // "처리반장"을 치는 도중 "철" 상태 (받침이 다음 글자로 넘어가기 전)
        assertTrue("처리반장" in names("철"))
        assertTrue("닭갈비집" in names("달"))
        assertTrue("왕서방" in names("오"))
    }

    @Test
    fun numberSearchWithoutHyphens() {
        val r = engine.search(ready.index, "0101234")
        assertEquals("김철수", r.first().contact.displayName)
        assertEquals(MatchField.PHONE, r.first().field)
        assertEquals(0 until 7, r.first().phoneDigitRange)
        // 하이픈을 넣어도 같은 결과
        assertEquals("김철수", engine.search(ready.index, "010-1234").first().contact.displayName)
    }

    @Test
    fun numberSuffixAndInfix() {
        assertEquals("김철수", names("5678").first())
        assertTrue("이철수" in names("5551"))
    }

    @Test
    fun internationalStoredNumberFoundByDomesticDigits() {
        assertEquals("박영희", names("01022223333").first())
        assertEquals("박영희", names("+82 10 2222").first())
    }

    @Test
    fun shortNumberQueriesOnlyMatchPrefix() {
        // 한두 자리 숫자는 앞자리 일치만 허용해 결과가 과도하게 많아지지 않게 한다.
        assertEquals(listOf("고객센터"), names("12"))
        assertTrue(names("1").all { it == "고객센터" })
    }

    @Test
    fun organizationSearch() {
        val r = engine.search(ready.index, "한국전력")
        assertEquals("고객센터", r.first().contact.displayName)
        assertEquals(MatchField.ORGANIZATION, r.first().field)
        assertEquals("한국전력공사", r.first().matchedText)
    }

    @Test
    fun nicknameSearch() {
        val r = engine.search(ready.index, "영희쌤")
        assertEquals("박영희", r.first().contact.displayName)
        assertEquals(MatchField.NICKNAME, r.first().field)
    }

    @Test
    fun latinNameCaseInsensitiveAndWordStart() {
        assertEquals("Alice Kim", names("ALI").first())
        assertEquals("Alice Kim", names("kim").first())
    }

    @Test
    fun nameMatchesOutrankOrganizationMatches() {
        // "바로" 는 김철수의 회사명(바로상사)에만 있다 → 결과는 있지만 이름 정확 일치보다 낮다.
        val byOrg = engine.search(ready.index, "바로").first()
        assertEquals(MatchField.ORGANIZATION, byOrg.field)
        assertTrue(byOrg.score < 100)
    }

    @Test
    fun boostBreaksTies() {
        val plain = names("ㄱㅊ")
        assertEquals(listOf("김철민", "김철수"), plain)
        val boosted = engine.search(ready.index, "ㄱㅊ") { if (it.displayName == "김철수") 5 else 0 }.map { it.contact.displayName }
        assertEquals(listOf("김철수", "김철민"), boosted)
    }

    @Test
    fun emptyOrSymbolQueriesReturnNothing() {
        assertTrue(names("").isEmpty())
        assertTrue(names("   ").isEmpty())
        assertTrue(names("--").isEmpty())
    }

    @Test
    fun keypadNumberSearch() {
        val r = engine.searchByNumber(ready.index, "0107777")
        assertEquals("홍길동", r.single().contact.displayName)
        assertEquals("01077778888", r.single().matchedPhone?.nationalDigits)
    }

    @Test
    fun searchIsFastWithManyContacts() {
        val surnames = "김이박최정강조윤장임한오서신권황안송류홍".toList()
        val given = "민서준도윤하은지우현예수아시연호진영철희".toList()
        val many = (0 until 5000).map { i ->
            val name = "${surnames[i % surnames.size]}${given[(i / 7) % given.size]}${given[(i / 3) % given.size]}"
            TestContacts.contact(
                1000L + i,
                name,
                TestContacts.phone("010-%04d-%04d".format(i % 10000, (i * 7) % 10000)),
                organization = if (i % 5 == 0) "회사$i" else null,
            )
        }
        val big = ContactsState.Ready.build(ContactsState.Ready.sortContacts(many), engine)
        // 예열
        engine.search(big.index, "ㄱㅁ")
        val queries = listOf("ㄱ", "ㄱㅁ", "김민", "김민서", "010", "0101234", "5678", "회사")
        val elapsed = measureTimeMillis { repeat(5) { queries.forEach { engine.search(big.index, it) } } }
        val perQuery = elapsed.toDouble() / (5 * queries.size)
        println("5,000명 연락처 검색 1회 평균 %.2f ms".format(perQuery))
        assertTrue("검색이 너무 느립니다: $perQuery ms", perQuery < 50.0)
    }
}
