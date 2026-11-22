package io.github.herfar.barodial.core.util

/**
 * DataStore 에 목록을 문자열 하나로 저장하기 위한 간단한 인코더.
 * 행은 줄바꿈, 열은 탭으로 구분하고 역슬래시로 이스케이프한다.
 * (외부 직렬화 라이브러리 없이 순수 Kotlin 으로 테스트 가능하게 유지)
 */
object ListCodec {

    fun encode(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString("\t") { escape(it) } }

    fun decode(text: String?): List<List<String>> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split('\n').map { line -> line.split('\t').map { unescape(it) } }
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(value[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
