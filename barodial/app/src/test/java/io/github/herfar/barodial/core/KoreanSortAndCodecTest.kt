package io.github.herfar.barodial.core

import io.github.herfar.barodial.core.text.KoreanSort
import io.github.herfar.barodial.core.util.ListCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanSortAndCodecTest {

    @Test
    fun hangulThenLatinThenOthers() {
        val names = listOf("Bob", "김철수", "123 관리실", "가나다", "alice", "하늘", "까치", "#태그")
        val sorted = names.sortedWith(KoreanSort.nameComparator())
        assertEquals(listOf("가나다", "김철수", "까치", "하늘", "alice", "Bob", "#태그", "123 관리실").take(6), sorted.take(6))
        // 숫자·기호 시작은 맨 뒤
        assertEquals(setOf("#태그", "123 관리실"), sorted.takeLast(2).toSet())
    }

    @Test
    fun indexLabels() {
        assertEquals("ㄱ", KoreanSort.indexLabel("까치"))
        assertEquals("ㄱ", KoreanSort.indexLabel("김철수"))
        assertEquals("ㅎ", KoreanSort.indexLabel("홍길동"))
        assertEquals("Z", KoreanSort.indexLabel("zoe"))
        assertEquals("#", KoreanSort.indexLabel("1004"))
        assertEquals("#", KoreanSort.indexLabel(""))
    }

    @Test
    fun codecRoundTripWithSpecialCharacters() {
        val rows = listOf(
            listOf("lookup\tkey", "12", "이름\n둘째줄"),
            listOf("back\\slash", "", "plain"),
        )
        assertEquals(rows, ListCodec.decode(ListCodec.encode(rows)))
        assertEquals(emptyList<List<String>>(), ListCodec.decode(null))
    }
}
