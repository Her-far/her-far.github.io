package io.github.herfar.barodial.data.calllog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 같은 번호·같은 종류로 연달아 온 통화를 한 줄로 묶은 것. */
data class CallGroup(
    val latest: CallLogEntry,
    val count: Int,
    val entries: List<CallLogEntry>,
)

data class CallDaySection(val date: LocalDate, val groups: List<CallGroup>)

data class CallStats(val totalCalls: Int, val totalDurationSeconds: Long, val missed: Int)

object CallLogGrouping {

    fun filter(entries: List<CallLogEntry>, filter: CallFilter): List<CallLogEntry> = when (filter) {
        CallFilter.ALL -> entries
        CallFilter.MISSED -> entries.filter { it.type == CallType.MISSED || it.type == CallType.REJECTED }
        CallFilter.INCOMING -> entries.filter { it.type == CallType.INCOMING }
        CallFilter.OUTGOING -> entries.filter { it.type == CallType.OUTGOING }
        CallFilter.RECORDED -> entries
    }

    /** 최신순 목록을 날짜별로 나누고, 날짜 안에서 연속된 같은 번호·종류를 묶는다. */
    fun groupByDay(entries: List<CallLogEntry>, zone: ZoneId = ZoneId.systemDefault()): List<CallDaySection> {
        val sections = ArrayList<CallDaySection>()
        var currentDate: LocalDate? = null
        var groups = ArrayList<CallGroup>()
        var run = ArrayList<CallLogEntry>()

        fun flushRun() {
            if (run.isNotEmpty()) {
                groups += CallGroup(run.first(), run.size, run.toList())
                run = ArrayList()
            }
        }

        fun flushDay() {
            flushRun()
            val date = currentDate
            if (date != null && groups.isNotEmpty()) sections += CallDaySection(date, groups)
            groups = ArrayList()
        }

        for (e in entries) {
            val date = Instant.ofEpochMilli(e.timestampMillis).atZone(zone).toLocalDate()
            if (date != currentDate) {
                flushDay()
                currentDate = date
            }
            val last = run.lastOrNull()
            if (last != null && (last.nationalDigits != e.nationalDigits || last.type != e.type)) flushRun()
            run += e
        }
        flushDay()
        return sections
    }

    fun forNumbers(entries: List<CallLogEntry>, nationalDigits: Set<String>): List<CallLogEntry> =
        entries.filter { it.nationalDigits in nationalDigits }

    fun stats(entries: List<CallLogEntry>): CallStats = CallStats(
        totalCalls = entries.size,
        totalDurationSeconds = entries.sumOf { it.durationSeconds },
        missed = entries.count { it.type == CallType.MISSED },
    )
}
