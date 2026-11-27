package io.github.herfar.barodial.data.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchRulesTest {

    @Test
    fun businessNamesAreSearchedOnline() {
        assertEquals(OnlineQuery("전기공사협회", false), PlaceSearchRules.parse("전기공사협회"))
        assertEquals(OnlineQuery("한국전력공사", false), PlaceSearchRules.parse("  한국전력공사 "))
        assertEquals(OnlineQuery("김포시청", false), PlaceSearchRules.parse("김포시청"))
        assertEquals(OnlineQuery("스타벅스 김포점", false), PlaceSearchRules.parse("스타벅스   김포점"))
    }

    @Test
    fun shortNumericAndChoseongQueriesAreNotSent() {
        assertNull("1글자", PlaceSearchRules.parse("김"))
        assertNull(PlaceSearchRules.parse(" "))
        assertNull("번호는 기기 안에서만", PlaceSearchRules.parse("010-1234-5678"))
        assertNull(PlaceSearchRules.parse("1588"))
        assertNull("초성 검색", PlaceSearchRules.parse("ㄱㅊㅅ"))
        assertNull("조합 중", PlaceSearchRules.parse("김포시ㅊ"))
        assertNull("너무 김", PlaceSearchRules.parse("가".repeat(51)))
    }

    @Test
    fun nearbyWords() {
        assertEquals(OnlineQuery("약국", true), PlaceSearchRules.parse("근처 약국"))
        assertEquals(OnlineQuery("약국", true), PlaceSearchRules.parse("근처약국"))
        assertEquals(OnlineQuery("약국", true), PlaceSearchRules.parse("약국 근처"))
        assertEquals(OnlineQuery("편의점", true), PlaceSearchRules.parse("내 주변 편의점"))
        assertEquals(OnlineQuery("주유소", true), PlaceSearchRules.parse("가까운 주유소"))
        val name = PlaceSearchRules.parse("가까운나라")
        assertFalse("띄어 쓰지 않은 '가까운'은 이름의 일부", name!!.wantsNearby)
        assertNull("'근처'만으로는 보내지 않는다", PlaceSearchRules.parse("근처"))
        assertNull(PlaceSearchRules.parse("근처 약"))
    }
}

class PlaceRankingTest {

    private fun place(id: String, name: String, address: String = "경기 김포시 예시로 1", distance: Int? = null) =
        Place(id, name, "031-000-0000", address, "", "", "", "http://place.map.kakao.com/$id", distance)

    @Test
    fun exactNameComesFirstThenPrefixThenContains() {
        val ranked = PlaceRanking.rank(
            "김포시청",
            listOf(place("1", "김포시청 민원실"), place("2", "김포 맛집"), place("3", "새김포시청로 식당"), place("4", "김포시청")),
            byDistance = false,
        )
        assertEquals(listOf("4", "1", "3", "2"), ranked.map { it.place.id })
        assertTrue(ranked.first().exactMatch)
    }

    @Test
    fun sameNameBranchesGetRegionLabels() {
        val ranked = PlaceRanking.rank(
            "한국전력공사",
            listOf(
                place("a", "한국전력공사", "전남 나주시 예시로 5"),
                place("b", "한국전력공사", "서울 중구 예시로 6"),
                place("c", "한국전력공사 김포지사", "경기 김포시 예시로 7"),
            ),
            byDistance = false,
        )
        assertEquals("전남 나주시", ranked.first { it.place.id == "a" }.regionLabel)
        assertEquals("서울 중구", ranked.first { it.place.id == "b" }.regionLabel)
        assertNull("이름이 다르면 지역 표시 없음", ranked.first { it.place.id == "c" }.regionLabel)
    }

    @Test
    fun distanceOrderIsKeptExceptExactMatch() {
        val ranked = PlaceRanking.rank(
            "약국",
            listOf(place("1", "예시약국", distance = 100), place("2", "약국", distance = 900), place("3", "바로약국", distance = 50)),
            byDistance = true,
        )
        assertEquals(listOf("2", "1", "3"), ranked.map { it.place.id })
    }

    @Test
    fun mergeSkipsDuplicates() {
        val merged = PlaceRanking.merge(listOf(place("1", "가"), place("2", "나")), listOf(place("2", "나"), place("3", "다")))
        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }
}
