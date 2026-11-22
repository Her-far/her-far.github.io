package io.github.herfar.barodial.core

import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanPhoneNumbersTest {

    @Test
    fun mobileNumbers() {
        assertEquals("010-1234-5678", KoreanPhoneNumbers.format("01012345678"))
        assertEquals("010-123-4567", KoreanPhoneNumbers.format("0101234567"))
        assertEquals("011-123-4567", KoreanPhoneNumbers.format("0111234567"))
        assertEquals("070-1234-5678", KoreanPhoneNumbers.format("07012345678"))
    }

    @Test
    fun mobileAsYouType() {
        assertEquals("010", KoreanPhoneNumbers.format("010"))
        assertEquals("010-1", KoreanPhoneNumbers.format("0101"))
        assertEquals("010-1234", KoreanPhoneNumbers.format("0101234"))
        assertEquals("010-1234-5", KoreanPhoneNumbers.format("01012345"))
        assertEquals("010-1234-56", KoreanPhoneNumbers.format("010123456"))
    }

    @Test
    fun seoulAndAreaCodes() {
        assertEquals("02-12", KoreanPhoneNumbers.format("0212"))
        assertEquals("02-123-4567", KoreanPhoneNumbers.format("021234567"))
        assertEquals("02-1234-5678", KoreanPhoneNumbers.format("0212345678"))
        assertEquals("031-123-4567", KoreanPhoneNumbers.format("0311234567"))
        assertEquals("031-1234-5678", KoreanPhoneNumbers.format("03112345678"))
        assertEquals("064-123", KoreanPhoneNumbers.format("064123"))
        assertEquals("0505-123-4567", KoreanPhoneNumbers.format("05051234567"))
        assertEquals("080-123-4567", KoreanPhoneNumbers.format("0801234567"))
    }

    @Test
    fun representativeAndSpecialNumbers() {
        assertEquals("1588-1234", KoreanPhoneNumbers.format("15881234"))
        assertEquals("1588-12", KoreanPhoneNumbers.format("158812"))
        assertEquals("112", KoreanPhoneNumbers.format("112"))
        assertEquals("119", KoreanPhoneNumbers.format("119"))
        assertEquals("*#06#", KoreanPhoneNumbers.format("*#06#"))
    }

    @Test
    fun internationalFormat() {
        assertEquals("+82 10-1234-5678", KoreanPhoneNumbers.format("+821012345678"))
        assertEquals("+82 2-123-4567", KoreanPhoneNumbers.format("+8221234567"))
        assertEquals("+1 650-555-1234", KoreanPhoneNumbers.format("+1 650-555-1234"))
    }

    @Test
    fun alreadyFormattedInputIsNormalised() {
        assertEquals("010-1234-5678", KoreanPhoneNumbers.format("010 1234 5678"))
        assertEquals("010-1234-5678", KoreanPhoneNumbers.format("010-1234-5678"))
        assertEquals("(02) 123-4567", KoreanPhoneNumbers.format("(02) 123-4567"))
    }

    @Test
    fun nationalDigits() {
        assertEquals("01012345678", KoreanPhoneNumbers.toNationalDigits("+82 10-1234-5678"))
        assertEquals("01012345678", KoreanPhoneNumbers.toNationalDigits("+82 010-1234-5678"))
        assertEquals("01012345678", KoreanPhoneNumbers.toNationalDigits("010-1234-5678"))
        assertEquals("0212345678", KoreanPhoneNumbers.toNationalDigits("0082 2 1234 5678"))
        assertEquals("16505551234", KoreanPhoneNumbers.toNationalDigits("+1 650-555-1234"))
    }

    @Test
    fun digitRangeMapping() {
        // "010-1234-5678" 에서 숫자 3..6 ("1234") 은 문자열 4..7
        assertEquals(4..7, KoreanPhoneNumbers.mapDigitRange("010-1234-5678", 3, 7))
        assertEquals(0..5, KoreanPhoneNumbers.mapDigitRange("010-1234-5678", 0, 5))
        assertEquals(9..12, KoreanPhoneNumbers.mapDigitRange("010-1234-5678", 7, 11))
    }

    @Test
    fun dialInputSanitising() {
        assertEquals("01012345678", KoreanPhoneNumbers.sanitizeDialInput("tel: 010-1234-5678"))
        assertEquals("+82", KoreanPhoneNumbers.sanitizeDialInput("++82"))
        assertEquals("*#06#", KoreanPhoneNumbers.sanitizeDialInput("*#06#"))
        assertEquals("", KoreanPhoneNumbers.sanitizeDialInput("전화번호 없음"))
    }

    @Test
    fun phoneQueryDetection() {
        assertTrue(KoreanPhoneNumbers.looksLikePhoneQuery("010-1234"))
        assertTrue(KoreanPhoneNumbers.looksLikePhoneQuery("02 555"))
        assertTrue(KoreanPhoneNumbers.looksLikePhoneQuery("+82 10"))
        assertFalse(KoreanPhoneNumbers.looksLikePhoneQuery("김철수"))
        assertFalse(KoreanPhoneNumbers.looksLikePhoneQuery("302호"))
        assertFalse(KoreanPhoneNumbers.looksLikePhoneQuery("--"))
    }

    @Test
    fun directCallSafety() {
        assertTrue(KoreanPhoneNumbers.requiresDialerConfirmation("112"))
        assertTrue(KoreanPhoneNumbers.requiresDialerConfirmation("119"))
        assertTrue(KoreanPhoneNumbers.requiresDialerConfirmation("*#06#"))
        assertTrue(KoreanPhoneNumbers.requiresDialerConfirmation("010,1234"))
        assertFalse(KoreanPhoneNumbers.requiresDialerConfirmation("01012345678"))
        assertFalse(KoreanPhoneNumbers.requiresDialerConfirmation("15881234"))
    }
}
