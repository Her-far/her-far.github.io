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
 * @param label 파일 이름에서 읽은 상대 이름 또는 번호 (예: "홍길동", "010-1234-5678")
 * @param labelDigits [label] 이 번호라면 국내 숫자 형식
 * @param startMillis 녹음 시작 시각 (파일 이름의 시각, 없으면 수정 시각 - 길이로 추정)
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
)

/** 녹음 파일 이름 해석 결과 */
data class ParsedRecordingName(val label: String?, val labelDigits: String?, val startMillis: Long?)

/**
 * 통화 녹음 파일 이름 해석.
 *
 * 삼성 전화의 파일 이름 형식은 공식 문서가 없어 여러 형태를 모두 받아들인다.
 *  - "통화 녹음 홍길동_231115_143022.m4a"
 *  - "Call recording 010-1234-5678_20231115_143022.m4a"
 *  - "홍길동 231115 1430.m4a"
 */
object RecordingFileNames {
    private val PREFIXES = listOf("통화 녹음", "통화녹음", "call recording", "callrecording", "record", "녹음")
    /** 파일 이름 끝의 "날짜_시각" (번호 속 숫자를 날짜로 잘못 읽지 않도록 끝에서 찾는다) */
    private val TIMESTAMP_END = Regex("""(?<!\d)(\d{8}|\d{6})[ _\-.]?(\d{6}|\d{4})$""")
    private val TIMESTAMP_ANY = Regex("""(?<!\d)(\d{8}|\d{6})[ _\-.]?(\d{6}|\d{4})(?!\d)""")
    private val DUPLICATE_SUFFIX = Regex("""\s*\(\d+\)$""")

    fun parse(fileName: String, zone: ZoneId = ZoneId.systemDefault()): ParsedRecordingName {
        val base = fileName.substringBeforeLast('.', fileName).replace(DUPLICATE_SUFFIX, "").trim()
        val match = TIMESTAMP_END.find(base) ?: TIMESTAMP_ANY.findAll(base).lastOrNull()
        val start = match?.let { toMillis(it.groupValues[1], it.groupValues[2], zone) }
        var label = (if (match != null) base.substring(0, match.range.first) else base).trim()
        for (p in PREFIXES) {
            if (label.lowercase().startsWith(p)) {
                label = label.substring(p.length)
                break
            }
        }
        label = label.trim(' ', '_', '-', '.', '(', ')')
        if (label.isEmpty()) return ParsedRecordingName(null, null, start)
        // "홍길동 (010-1234-5678)" 같은 형태에서 번호를 따로 읽는다.
        val digits = KoreanPhoneNumbers.digitsOnly(label)
        val labelDigits = if (digits.length >= 7) KoreanPhoneNumbers.toNationalDigits(digitsFromLabel(label)) else null
        val name = if (labelDigits != null && KoreanPhoneNumbers.looksLikePhoneQuery(label)) label else label.substringBefore('(').trim().ifEmpty { label }
        return ParsedRecordingName(name, labelDigits, start)
    }

    private fun digitsFromLabel(label: String): String {
        val inParens = label.substringAfter('(', "").substringBefore(')', "")
        val source = if (KoreanPhoneNumbers.digitsOnly(inParens).length >= 7) inParens else label
        return source.filter { it.isDigit() || it == '+' }
    }

    private fun toMillis(datePart: String, timePart: String, zone: ZoneId): Long? = try {
        val date = when (datePart.length) {
            8 -> LocalDate.of(datePart.substring(0, 4).toInt(), datePart.substring(4, 6).toInt(), datePart.substring(6, 8).toInt())
            else -> LocalDate.of(2000 + datePart.substring(0, 2).toInt(), datePart.substring(2, 4).toInt(), datePart.substring(4, 6).toInt())
        }
        val time = when (timePart.length) {
            6 -> LocalTime.of(timePart.substring(0, 2).toInt(), timePart.substring(2, 4).toInt(), timePart.substring(4, 6).toInt())
            else -> LocalTime.of(timePart.substring(0, 2).toInt(), timePart.substring(2, 4).toInt())
        }
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/** 통화기록·연락처와 녹음 파일을 짝짓는다 (순수 Kotlin). */
object RecordingMatcher {
    /** 통화 시작 전 이만큼 일찍 시작한 녹음까지 허용 (기기 시각 오차) */
    private const val EARLY_SLACK_MS = 60_000L

    /** 통화가 끝난 뒤 이만큼 늦게 시작한 녹음까지 허용 */
    private const val LATE_SLACK_MS = 120_000L

    /** 이름·번호를 알 수 없는 녹음은 통화 시작 직후 짧은 구간만 허용 */
    private const val ANONYMOUS_WINDOW_MS = 90_000L

    /**
     * 통화기록 id → 녹음. 녹음 하나는 가장 가까운 통화 하나에만 연결된다.
     * @param nameFor 통화 상대의 표시 이름 (연락처 이름 또는 통화기록에 캐시된 이름)
     */
    fun match(
        calls: List<CallLogEntry>,
        recordings: List<CallRecording>,
        nameFor: (CallLogEntry) -> String?,
    ): Map<Long, CallRecording> {
        val candidates = ArrayList<Triple<Long, CallRecording, Long>>()
        for (call in calls) {
            if (call.type == CallType.MISSED || call.type == CallType.REJECTED || call.type == CallType.BLOCKED) continue
            val name = nameFor(call)
            for (rec in recordings) {
                val identity = identityMatches(rec, call.nationalDigits, name)
                if (identity == false) continue
                val start = call.timestampMillis
                val end = start + call.durationSeconds * 1000
                val inWindow = if (identity == true) {
                    rec.startMillis in (start - EARLY_SLACK_MS)..(end + LATE_SLACK_MS)
                } else {
                    rec.startMillis in (start - EARLY_SLACK_MS)..(start + ANONYMOUS_WINDOW_MS)
                }
                if (inWindow) candidates += Triple(call.id, rec, abs(rec.startMillis - start))
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
        val names = (listOf(contact.displayName) + contact.nicknames).mapTo(HashSet()) { SearchText.normalize(it) }
        val byLabel = recordings.filter { rec ->
            (rec.labelDigits != null && rec.labelDigits in digits) ||
                (rec.label != null && SearchText.normalize(rec.label) in names)
        }
        val byCall = calls.filter { it.nationalDigits in digits }.mapNotNull { matchedByCall[it.id] }
        return (byLabel + byCall).distinctBy { it.id }.sortedByDescending { it.startMillis }
    }

    /** true: 같은 사람, false: 다른 사람, null: 파일 이름으로 판단 불가 */
    private fun identityMatches(rec: CallRecording, callDigits: String, callName: String?): Boolean? {
        if (rec.labelDigits != null && callDigits.isNotEmpty()) return rec.labelDigits == callDigits
        if (rec.label != null && callName != null) return SearchText.normalize(rec.label) == SearchText.normalize(callName)
        if (rec.label != null && callName == null) return null
        return null
    }
}
