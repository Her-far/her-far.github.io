package io.github.herfar.barodial.core.phone

/**
 * 한국 전화번호 정규화·표시 형식.
 *
 * 입력 중(as-you-type)과 저장된 번호 표시에 같은 규칙을 쓴다.
 *  - 휴대전화 010-1234-5678 / 011-123-4567
 *  - 서울 02-123-4567 / 02-1234-5678
 *  - 지역 031-123-4567 / 031-1234-5678
 *  - 인터넷전화 070-1234-5678, 평생번호 0505-123-4567
 *  - 전국대표번호 1588-1234
 *  - 국제형식 +82 10-1234-5678
 */
object KoreanPhoneNumbers {

    private val AREA_CODES_3 = setOf(
        "031", "032", "033", "041", "042", "043", "044",
        "051", "052", "053", "054", "055", "061", "062", "063", "064",
        "030", "060", "080",
    )
    private val LEGACY_MOBILE = setOf("011", "016", "017", "018", "019")
    private val FOUR_FOUR = setOf("010", "070")
    private val REPRESENTATIVE_PREFIXES = setOf("15", "16", "18")

    /** 다이얼 가능한 문자: 숫자, +, *, #, 쉼표(일시정지), 세미콜론(대기) */
    fun isDialChar(c: Char): Boolean = c.isDigit() || c == '+' || c == '*' || c == '#' || c == ',' || c == ';'

    fun digitsOnly(text: CharSequence): String = buildString(text.length) {
        for (c in text) if (c in '0'..'9') append(c)
    }

    /** 키패드 입력/붙여넣기 값을 다이얼 문자만 남기도록 정리한다. '+'는 맨 앞만 허용. */
    fun sanitizeDialInput(text: CharSequence): String = buildString {
        for (c in text) {
            if (!isDialChar(c)) continue
            if (c == '+' && isNotEmpty()) continue
            append(c)
        }
    }

    /**
     * 비교·검색용 국내 숫자 형식.
     * "+82 10-1234-5678" → "01012345678", "010-1234-5678" → "01012345678"
     */
    fun toNationalDigits(raw: String): String {
        val trimmed = raw.trim()
        val digits = digitsOnly(trimmed)
        val international = when {
            trimmed.startsWith("+82") -> digits.removePrefix("82")
            trimmed.startsWith("0082") -> digits.removePrefix("0082")
            else -> return digits
        }
        if (international.isEmpty()) return ""
        return if (international.startsWith("0")) international else "0$international"
    }

    /** 검색어가 전화번호 형태인지 (숫자와 전화번호 기호만 포함). */
    fun looksLikePhoneQuery(query: CharSequence): Boolean {
        var digitCount = 0
        for (c in query) {
            when {
                c in '0'..'9' -> digitCount++
                c == '+' || c == '-' || c == ' ' || c == '(' || c == ')' || c == '.' || c == '*' || c == '#' -> Unit
                else -> return false
            }
        }
        return digitCount > 0
    }

    /** 원터치(ACTION_CALL) 대신 반드시 다이얼 화면을 거쳐야 하는 번호: 긴급·특수번호, USSD/MMI 코드 */
    fun requiresDialerConfirmation(number: String): Boolean {
        if (number.any { it == '*' || it == '#' || it == ',' || it == ';' }) return true
        val digits = digitsOnly(number)
        return digits.length <= 4
    }

    /** 화면 표시용 형식. 알 수 없는 형식은 원래 값을 그대로 돌려준다. */
    fun format(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("+")) {
            val digits = digitsOnly(text)
            if (text.drop(1).any { !it.isDigit() && it != ' ' && it != '-' }) return text
            if (digits.startsWith("82") && digits.length > 2) {
                val national = "0" + digits.substring(2).removePrefix("0")
                val formatted = formatNational(national)
                return "+82 " + formatted.removePrefix("0")
            }
            return text
        }
        if (text.any { !it.isDigit() && it != '-' && it != ' ' }) return text
        return formatNational(digitsOnly(text))
    }

    /** 숫자만으로 된 국내 번호 형식화 (입력 중인 번호도 처리). */
    fun formatNational(d: String): String {
        if (d.length < 3) return d
        // 전국대표번호 1588-1234
        if (d[0] == '1' && d.take(2) in REPRESENTATIVE_PREFIXES) {
            return when {
                d.length <= 4 -> d
                d.length <= 8 -> d.substring(0, 4) + "-" + d.substring(4)
                else -> d
            }
        }
        if (d[0] != '0') return d
        if (d.startsWith("02")) return groupArea(d, 2)
        val prefix3 = d.take(3)
        return when {
            prefix3 in FOUR_FOUR -> groupFourFour(d)
            prefix3 in LEGACY_MOBILE -> groupArea(d, 3)
            prefix3 == "050" -> if (d.length >= 4) groupArea(d, 4) else d
            prefix3 in AREA_CODES_3 -> groupArea(d, 3)
            else -> d
        }
    }

    /** 지역번호 + 3/4자리 국번 + 4자리 */
    private fun groupArea(d: String, areaLen: Int): String {
        val area = d.substring(0, areaLen)
        val rest = d.substring(areaLen)
        return when {
            rest.isEmpty() -> area
            rest.length <= 3 -> "$area-$rest"
            rest.length <= 7 -> "$area-${rest.substring(0, 3)}-${rest.substring(3)}"
            rest.length == 8 -> "$area-${rest.substring(0, 4)}-${rest.substring(4)}"
            else -> d
        }
    }

    /** 010/070: 입력 중에도 4-4 로 묶고, 완성된 10자리 번호만 3-4 로 표시한다. */
    private fun groupFourFour(d: String): String {
        val area = d.substring(0, 3)
        val rest = d.substring(3)
        return when {
            rest.isEmpty() -> area
            rest.length <= 4 -> "$area-$rest"
            rest.length == 7 -> "$area-${rest.substring(0, 3)}-${rest.substring(3)}"
            rest.length <= 8 -> "$area-${rest.substring(0, 4)}-${rest.substring(4)}"
            else -> d
        }
    }

    /**
     * 숫자 문자열의 구간 [digitStart, digitEnd) 이 형식화된 문자열에서 차지하는 구간(inclusive).
     * 검색 결과에서 일치한 번호 부분을 강조할 때 사용한다.
     */
    fun mapDigitRange(formatted: String, digitStart: Int, digitEndExclusive: Int): IntRange? {
        if (digitEndExclusive <= digitStart) return null
        var digitIndex = 0
        var first = -1
        var last = -1
        for ((i, c) in formatted.withIndex()) {
            if (c !in '0'..'9') continue
            if (digitIndex == digitStart) first = i
            if (digitIndex == digitEndExclusive - 1) {
                last = i
                break
            }
            digitIndex++
        }
        return if (first >= 0 && last >= first) first..last else null
    }
}
