package io.github.herfar.barodial.data.recordings

import io.github.herfar.barodial.core.hangul.SearchText
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.contacts.Contact
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * 삼성 전화 등이 저장한 통화 녹음 파일 하나 (MediaStore 오디오 항목).
 *
 * @param label 파일 이름에서 읽은 상대 이름 또는 번호 (예: "홍길동", "전기신문 정세영(레아)", "010-1234-5678")
 * @param labelDigits 파일 이름에 번호가 있으면 국내 숫자 형식
 * @param startMillis 녹음 시각 (파일 이름의 시각, 없으면 수정 시각 - 길이로 추정)
 * @param timeFromFileName [startMillis] 를 파일 이름에서 읽었는지
 */
data class CallRecording(
    val id: Long,
    val uri: String,
    val displayName: String,
    val label: String?,
    val labelDigits: String?,
    val startMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
    val relativePath: String? = null,
    val timeFromFileName: Boolean = true,
)

/** 녹음 파일 이름 해석 결과 */
data class ParsedRecordingName(val label: String?, val labelDigits: String?, val startMillis: Long?)

/**
 * 통화 녹음 파일 이름 해석.
 *
 * 삼성 전화의 파일 이름 형식은 공식 문서가 없어 여러 형태를 모두 받아들인다.
 *  - "통화 녹음 홍길동_231115_143022.m4a"
 *  - "통화 녹음 전기신문 정세영(레아)_251123_124312.m4a"
 *  - "Call recording 010-1234-5678_20231115_143022.m4a"
 *  - "통화 녹음 01012345678_251123124312.m4a" (날짜와 시각 사이 구분자 없음)
 *  - "홍길동 231115 1430.m4a"
 */
object RecordingFileNames {
    private val PREFIXES = listOf("통화 녹음", "통화녹음", "call recording", "callrecording", "record", "녹음")

    /** 끝의 "날짜<구분자>시각" (예: _251123_124312) */
    private val TWO_PART_END = Regex("""(?<!\d)(\d{6}|\d{8})[ _\-.](\d{4}|\d{6})$""")

    /** 끝의 구분자 없는 "날짜시각" (예: _251123124312) */
    private val ONE_PART_END = Regex("""(?<!\d)(\d{10}|\d{12}|\d{14})$""")
    private val DUPLICATE_SUFFIX = Regex("""\s*\(\d+\)$""")

    fun parse(fileName: String, zone: ZoneId = ZoneId.systemDefault()): ParsedRecordingName {
        val base = fileName.substringBeforeLast('.', fileName).replace(DUPLICATE_SUFFIX, "").trim()
        var start: Long? = null
        var labelEnd = base.length
        TWO_PART_END.find(base)?.let { m ->
            start = toMillis(m.groupValues[1], m.groupValues[2], zone)
            if (start != null) labelEnd = m.range.first
        }
        if (start == null) {
            ONE_PART_END.find(base)?.let { m ->
                val digits = m.groupValues[1]
                // 가능한 날짜/시각 길이 조합 중 올바른 날짜가 되는 첫 번째를 쓴다.
                for ((dateLen, timeLen) in listOf(8 to 6, 6 to 6, 8 to 4, 6 to 4)) {
                    if (dateLen + timeLen != digits.length) continue
                    val t = toMillis(digits.substring(0, dateLen), digits.substring(dateLen), zone) ?: continue
                    start = t
                    labelEnd = m.range.first
                    break
                }
            }
        }
        var label = base.substring(0, labelEnd).trim()
        for (p in PREFIXES) {
            if (label.lowercase().startsWith(p)) {
                label = label.substring(p.length)
                break
            }
        }
        label = label.trim(' ', '_', '-', '.')
        if (label.isEmpty()) return ParsedRecordingName(null, null, start)
        val labelDigits = phoneDigitsIn(label)
        return ParsedRecordingName(label, labelDigits, start)
    }

    /** 이름 자체가 번호이거나, 괄호 안에 번호가 있으면 그 번호 (국내 숫자 형식). */
    private fun phoneDigitsIn(label: String): String? {
        if (KoreanPhoneNumbers.looksLikePhoneQuery(label) && KoreanPhoneNumbers.digitsOnly(label).length >= 3) {
            return KoreanPhoneNumbers.toNationalDigits(label.filter { it.isDigit() || it == '+' })
        }
        val inParens = label.substringAfter('(', "").substringBefore(')', "")
        if (inParens.isNotEmpty() && KoreanPhoneNumbers.looksLikePhoneQuery(inParens) && KoreanPhoneNumbers.digitsOnly(inParens).length >= 7) {
            return KoreanPhoneNumbers.toNationalDigits(inParens.filter { it.isDigit() || it == '+' })
        }
        return null
    }

    private fun toMillis(datePart: String, timePart: String, zone: ZoneId): Long? = try {
        val date = when (datePart.length) {
            8 -> LocalDate.of(datePart.substring(0, 4).toInt(), datePart.substring(4, 6).toInt(), datePart.substring(6, 8).toInt())
            6 -> LocalDate.of(2000 + datePart.substring(0, 2).toInt(), datePart.substring(2, 4).toInt(), datePart.substring(4, 6).toInt())
            else -> null
        }
        val time = when (timePart.length) {
            6 -> LocalTime.of(timePart.substring(0, 2).toInt(), timePart.substring(2, 4).toInt(), timePart.substring(4, 6).toInt())
            4 -> LocalTime.of(timePart.substring(0, 2).toInt(), timePart.substring(2, 4).toInt())
            else -> null
        }
        if (date == null || time == null || date.year !in 2000..2099) null else LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/** 통화기록·연락처와 녹음 파일을 짝짓는다 (순수 Kotlin). */
object RecordingMatcher {
    /** 통화 시작보다 이만큼 이른 녹음까지 허용 (기기 시각·파일 시각 오차) */
    private const val EARLY_SLACK_MS = 90_000L

    /** 통화가 끝난 뒤 이만큼 늦은 녹음까지 허용 (파일 시각이 녹음 종료 시각인 경우 포함) */
    private const val LATE_SLACK_MS = 120_000L

    /** 녹음 길이가 통화 길이보다 이만큼 이상 길면 다른 통화의 녹음이다 */
    private const val DURATION_SLACK_MS = 20_000L

    /** 이름이 달라 보이지만(표기 차이 가능) 시각이 맞는 경우의 감점 */
    private const val NAME_MISMATCH_PENALTY_MS = 10 * 60_000L

    /** 파일 이름에 이름·번호가 없는 경우의 감점 */
    private const val ANONYMOUS_PENALTY_MS = 30_000L

    enum class Identity { SAME, UNKNOWN, NAME_DIFFERS, NUMBER_DIFFERS }

    /**
     * 통화기록 id → 녹음. 점수(시각 차이 + 이름 불일치 감점)가 가장 좋은 짝부터 정한다.
     * 녹음 하나는 통화 하나에만, 통화 하나에는 녹음 하나만 연결된다.
     * @param nameFor 통화 상대의 표시 이름 (연락처 이름 또는 통화기록에 캐시된 이름)
     */
    fun match(
        calls: List<CallLogEntry>,
        recordings: List<CallRecording>,
        nameFor: (CallLogEntry) -> String?,
    ): Map<Long, CallRecording> {
        if (calls.isEmpty() || recordings.isEmpty()) return emptyMap()
        val sortedRecs = recordings.sortedBy { it.startMillis }
        val candidates = ArrayList<Triple<Long, CallRecording, Long>>()
        for (call in calls) {
            if (call.type == CallType.MISSED || call.type == CallType.REJECTED || call.type == CallType.BLOCKED) continue
            if (call.durationSeconds <= 0) continue
            val start = call.timestampMillis
            val durationMs = call.durationSeconds * 1000
            val from = start - EARLY_SLACK_MS
            val to = start + durationMs + LATE_SLACK_MS
            val name = nameFor(call)
            // 시각 구간에 드는 녹음만 살펴본다 (이진 탐색)
            var i = lowerBound(sortedRecs, from)
            while (i < sortedRecs.size && sortedRecs[i].startMillis <= to) {
                val rec = sortedRecs[i++]
                if (rec.durationMillis > 0 && rec.durationMillis > durationMs + DURATION_SLACK_MS) continue
                val identity = identity(rec, call.nationalDigits, name)
                if (identity == Identity.NUMBER_DIFFERS) continue
                val penalty = when (identity) {
                    Identity.SAME -> 0L
                    Identity.UNKNOWN -> ANONYMOUS_PENALTY_MS
                    Identity.NAME_DIFFERS -> NAME_MISMATCH_PENALTY_MS
                    Identity.NUMBER_DIFFERS -> continue
                }
                candidates += Triple(call.id, rec, abs(rec.startMillis - start) + penalty)
            }
        }
        val result = HashMap<Long, CallRecording>()
        val usedRecordings = HashSet<Long>()
        for ((callId, rec, _) in candidates.sortedBy { it.third }) {
            if (callId in result || rec.id in usedRecordings) continue
            result[callId] = rec
            usedRecordings += rec.id
        }
        return result
    }

    /** 연락처 한 명의 녹음: 파일 이름의 이름/번호가 같거나, 그 사람의 통화에 연결된 녹음 */
    fun forContact(contact: Contact, recordings: List<CallRecording>, matchedByCall: Map<Long, CallRecording>, calls: List<CallLogEntry>): List<CallRecording> {
        val digits = contact.phones.mapTo(HashSet()) { it.nationalDigits }
        val names = listOf(contact.displayName) + contact.nicknames
        val byLabel = recordings.filter { rec ->
            (rec.labelDigits != null && rec.labelDigits in digits) ||
                (rec.label != null && names.any { sameName(rec.label, it) })
        }
        val byCall = calls.filter { it.nationalDigits in digits }.mapNotNull { matchedByCall[it.id] }
        return (byLabel + byCall).distinctBy { it.id }.sortedByDescending { it.startMillis }
    }

    /** 연락처에 없는 번호 하나의 녹음 */
    fun forNumber(nationalDigits: String, recordings: List<CallRecording>, matchedByCall: Map<Long, CallRecording>, calls: List<CallLogEntry>): List<CallRecording> {
        val byLabel = recordings.filter { it.labelDigits == nationalDigits }
        val byCall = calls.filter { it.nationalDigits == nationalDigits }.mapNotNull { matchedByCall[it.id] }
        return (byLabel + byCall).distinctBy { it.id }.sortedByDescending { it.startMillis }
    }

    fun identity(rec: CallRecording, callDigits: String, callName: String?): Identity {
        if (rec.labelDigits != null && callDigits.isNotEmpty()) {
            return if (rec.labelDigits == callDigits) Identity.SAME else Identity.NUMBER_DIFFERS
        }
        val label = rec.label ?: return Identity.UNKNOWN
        if (callName.isNullOrBlank()) return Identity.UNKNOWN
        return if (sameName(label, callName)) Identity.SAME else Identity.NAME_DIFFERS
    }

    /**
     * 이름 비교. 공백·기호를 무시하고, 한쪽이 다른 쪽을 포함해도 같은 사람으로 본다
     * (파일 이름에서 일부 문자가 빠지거나 바뀌는 경우 대비).
     */
    fun sameName(a: String, b: String): Boolean {
        val na = SearchText.normalize(a)
        val nb = SearchText.normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val shorter = if (na.length <= nb.length) na else nb
        val longer = if (na.length <= nb.length) nb else na
        return shorter.length >= 2 && longer.contains(shorter)
    }

    private fun lowerBound(sorted: List<CallRecording>, from: Long): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].startMillis < from) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
