package io.github.herfar.barodial.data

import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.RecordingFileNames
import io.github.herfar.barodial.data.recordings.RecordingMatcher
import io.github.herfar.barodial.testing.TestContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecordingsTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun samsungKoreanFileName() {
        val p = RecordingFileNames.parse("통화 녹음 홍길동_231115_143022.m4a", zone)
        assertEquals("홍길동", p.label)
        assertNull(p.labelDigits)
        assertEquals(at(2023, 11, 15, 14, 30, 22), p.startMillis)
    }

    @Test
    fun numberInFileNameIsNotMistakenForDate() {
        val p = RecordingFileNames.parse("통화 녹음 01012345678_231115_1430.m4a", zone)
        assertEquals("01012345678", p.labelDigits)
        assertEquals(at(2023, 11, 15, 14, 30), p.startMillis)
    }

    @Test
    fun englishAndOtherFormats() {
        val en = RecordingFileNames.parse("Call recording 010-1234-5678_20231115_143022.m4a", zone)
        assertEquals("01012345678", en.labelDigits)
        assertEquals(at(2023, 11, 15, 14, 30, 22), en.startMillis)

        val spaced = RecordingFileNames.parse("김철수 231115 1430.m4a", zone)
        assertEquals("김철수", spaced.label)
        assertEquals(at(2023, 11, 15, 14, 30), spaced.startMillis)

        val withNumber = RecordingFileNames.parse("통화 녹음 김철수 (010-1234-5678)_231115_143022(1).m4a", zone)
        assertEquals("김철수", withNumber.label)
        assertEquals("01012345678", withNumber.labelDigits)
        assertEquals(at(2023, 11, 15, 14, 30, 22), withNumber.startMillis)

        val garbage = RecordingFileNames.parse("메모.m4a", zone)
        assertNull(garbage.startMillis)
    }

    private fun rec(id: Long, label: String?, digits: String?, start: Long) =
        CallRecording(id, "content://r/$id", "f$id.m4a", label, digits, start, 60_000, 1)

    private fun call(id: Long, digits: String, type: CallType, start: Long, dur: Long = 120) =
        CallLogEntry(id, digits, digits, null, type, start, dur)

    @Test
    fun matcherPairsRecordingWithClosestCallOfSamePerson() {
        val t = at(2025, 11, 22, 10, 0)
        val calls = listOf(
            call(1, "01012345678", CallType.OUTGOING, t, 300),
            call(2, "01012345678", CallType.INCOMING, t + 3_600_000, 60),
            call(3, "01099998888", CallType.INCOMING, t + 10_000, 60),
            call(4, "01012345678", CallType.MISSED, t + 7_200_000, 0),
        )
        val recs = listOf(
            rec(10, "01012345678", "01012345678", t + 20_000), // 1번 통화
            rec(11, "01012345678", "01012345678", t + 3_600_000 + 8_000), // 2번 통화
            rec(12, "01012345678", "01012345678", t + 7_200_000 + 5_000), // 부재중에는 녹음이 없다
        )
        val m = RecordingMatcher.match(calls, recs) { null }
        assertEquals(10L, m[1]?.id)
        assertEquals(11L, m[2]?.id)
        assertNull("다른 번호의 통화에는 연결하지 않음", m[3])
        assertNull("부재중 통화에는 연결하지 않음", m[4])
    }

    @Test
    fun matcherUsesContactNameWhenFileHasName() {
        val t = at(2025, 11, 22, 10, 0)
        val calls = listOf(call(1, "01012345678", CallType.INCOMING, t, 200), call(2, "01055556666", CallType.INCOMING, t + 5_000, 200))
        val recs = listOf(rec(10, "김철수", null, t + 30_000))
        val m = RecordingMatcher.match(calls, recs) { if (it.id == 1L) "김철수" else "박영희" }
        assertEquals(10L, m[1]?.id)
        assertNull(m[2])
    }

    @Test
    fun recordingsForContactByFileNameAndByCall() {
        val t = at(2025, 11, 22, 10, 0)
        val kim = TestContacts.KIM_CHEOLSU
        val byName = rec(1, "김철수", null, t)
        val byNumber = rec(2, "010-1234-5678", "01012345678", t - 1000)
        val other = rec(3, "박영희", null, t)
        val anonymous = rec(4, null, null, t + 100_000)
        val calls = listOf(call(9, "01012345678", CallType.INCOMING, t + 90_000, 60))
        val result = RecordingMatcher.forContact(kim, listOf(byName, byNumber, other, anonymous), mapOf(9L to anonymous), calls)
        assertEquals(listOf(4L, 1L, 2L), result.map { it.id })
        assertTrue(other !in result)
    }
}
