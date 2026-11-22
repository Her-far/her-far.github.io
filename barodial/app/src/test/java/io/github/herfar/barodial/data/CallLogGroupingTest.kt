package io.github.herfar.barodial.data

import io.github.herfar.barodial.data.calllog.CallFilter
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallLogGrouping
import io.github.herfar.barodial.data.calllog.CallType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CallLogGroupingTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(date: String, hour: Int) =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun call(id: Long, digits: String, type: CallType, millis: Long, duration: Long = 60) =
        CallLogEntry(id, digits, digits, null, type, millis, duration)

    private val entries = listOf(
        call(1, "01011112222", CallType.MISSED, at("2026-11-22", 15)),
        call(2, "01011112222", CallType.MISSED, at("2026-11-22", 14)),
        call(3, "01033334444", CallType.OUTGOING, at("2026-11-22", 10), 125),
        call(4, "01011112222", CallType.INCOMING, at("2026-11-21", 20), 30),
    )

    @Test
    fun groupsConsecutiveSameNumberAndTypePerDay() {
        val sections = CallLogGrouping.groupByDay(entries, zone)
        assertEquals(2, sections.size)
        assertEquals(LocalDate.parse("2026-11-22"), sections[0].date)
        assertEquals(listOf(2, 1), sections[0].groups.map { it.count })
        assertEquals(1L, sections[0].groups[0].latest.id)
        assertEquals(1, sections[1].groups.size)
    }

    @Test
    fun filters() {
        assertEquals(2, CallLogGrouping.filter(entries, CallFilter.MISSED).size)
        assertEquals(1, CallLogGrouping.filter(entries, CallFilter.OUTGOING).size)
        assertEquals(1, CallLogGrouping.filter(entries, CallFilter.INCOMING).size)
        assertEquals(4, CallLogGrouping.filter(entries, CallFilter.ALL).size)
    }

    @Test
    fun perPersonStats() {
        val forPerson = CallLogGrouping.forNumbers(entries, setOf("01011112222"))
        val stats = CallLogGrouping.stats(forPerson)
        assertEquals(3, stats.totalCalls)
        assertEquals(2, stats.missed)
        assertEquals(150L, stats.totalDurationSeconds)
    }
}
