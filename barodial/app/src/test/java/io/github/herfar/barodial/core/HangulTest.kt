package io.github.herfar.barodial.core

import io.github.herfar.barodial.core.hangul.Hangul
import io.github.herfar.barodial.core.hangul.HangulMatcher
import io.github.herfar.barodial.core.hangul.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HangulTest {

    @Test
    fun choseongExtraction() {
        assertEquals("ㄱㅊㅅ", Hangul.toChoseong("김철수"))
        assertEquals("ㅎㄱㅈㄹ", Hangul.toChoseong("한국전력"))
        assertEquals("ㄲㅊ A1", Hangul.toChoseong("까치 A1"))
    }

    @Test
    fun allChoseongDetection() {
        assertTrue(Hangul.isAllChoseong("ㄱㅊㅅ"))
        assertFalse(Hangul.isAllChoseong("김ㅊ"))
        assertFalse(Hangul.isAllChoseong(""))
    }

    @Test
    fun baseChoseongMergesDoubleConsonants() {
        assertEquals('ㄱ', Hangul.baseChoseongOf('까'))
        assertEquals('ㅅ', Hangul.baseChoseongOf('쌀'))
        assertEquals('ㅎ', Hangul.baseChoseongOf('한'))
        assertNull(Hangul.baseChoseongOf('A'))
    }

    @Test
    fun splitFinalMovesConsonantToNextSyllable() {
        assertEquals('처' to 'ㄹ', Hangul.splitFinal('철'))
        assertEquals('달' to 'ㄱ', Hangul.splitFinal('닭'))
        assertNull(Hangul.splitFinal('처'))
    }

    @Test
    fun partialSyllableWhileComposing() {
        assertTrue(Hangul.isPartialOf('처', '철')) // 받침 입력 전
        assertTrue(Hangul.isPartialOf('오', '왕')) // 이중모음 입력 중
        assertTrue(Hangul.isPartialOf('달', '닭')) // 겹받침 입력 중
        assertFalse(Hangul.isPartialOf('철', '처'))
        assertTrue(Hangul.isPartialOf('처', '청'))
        assertFalse(Hangul.isPartialOf('커', '철'))
    }

    @Test
    fun matcherHandlesChoseongAndMixedQueries() {
        assertEquals(0, HangulMatcher.indexOf("ㄱㅊㅅ", "김철수"))
        assertEquals(0, HangulMatcher.indexOf("김ㅊ", "김철수"))
        assertEquals(1, HangulMatcher.indexOf("철수", "김철수"))
        assertEquals(-1, HangulMatcher.indexOf("ㄱㅊㅁ", "김철수"))
    }

    @Test
    fun matcherHandlesImeCompositionOfLastSyllable() {
        // "김철수"를 입력하는 도중 화면에 잠깐 "김처"가 보인다.
        assertTrue(HangulMatcher.contains("김처", "김철수"))
        // "처리"를 입력하는 도중에는 "철"이 먼저 보인다.
        assertTrue(HangulMatcher.contains("철", "처리반장"))
        assertTrue(HangulMatcher.contains("달", "닭갈비"))
        // 마지막 글자가 아닌 곳은 정확히 일치해야 한다.
        assertFalse(HangulMatcher.contains("처수", "김철수"))
    }

    @Test
    fun latinIsCaseInsensitive() {
        assertEquals(0, HangulMatcher.indexOf("ali", "alice kim".let(SearchText::normalize)))
        assertTrue(HangulMatcher.contains("kim", SearchText.normalize("Alice Kim")))
    }

    @Test
    fun searchTextMapsBackToOriginalIndices() {
        val t = SearchText("(주) 한국 전력")
        assertEquals("주한국전력", t.normalized)
        // "한국전력" 은 정규화 문자열 1..4 → 원문 4..8
        assertEquals(4..8, t.toOriginalRange(1, 5))
        assertTrue(t.isWordStart(1))
        assertTrue(t.isWordStart(3))
        assertFalse(t.isWordStart(2))
    }
}
