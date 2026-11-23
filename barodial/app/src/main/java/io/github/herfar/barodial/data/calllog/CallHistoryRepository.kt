package io.github.herfar.barodial.data.calllog

import kotlinx.coroutines.flow.StateFlow

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL, OTHER }

/** RECORDED: 통화 녹음 파일이 연결된 통화만 (녹음 매칭 결과는 ViewModel 에서 적용) */
enum class CallFilter { ALL, MISSED, INCOMING, OUTGOING, RECORDED }

/** 통화기록 한 건 (android.provider.CallLog.Calls 한 행). */
data class CallLogEntry(
    val id: Long,
    val number: String,
    val nationalDigits: String,
    val cachedName: String?,
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long,
)

sealed interface CallHistoryAvailability {
    /** 권한을 아직 요청하지 않았다. */
    data class PermissionRequired(val permission: String) : CallHistoryAvailability

    /**
     * 권한이 거부되었다. READ_CALL_LOG 는 hard-restricted 권한이라,
     * 설치 프로그램이 허용 목록에 올리지 않았다면 대화상자 없이 즉시 거부된다.
     */
    data class Denied(val permission: String) : CallHistoryAvailability

    data object Available : CallHistoryAvailability
}

/**
 * 통화기록 공급원 (CallLog Provider 읽기 전용).
 * 앱의 나머지 부분은 이 인터페이스만 사용하므로, 테스트에서는 가짜 구현으로 바꿔 끼운다.
 * 권한이 없으면 어떤 경우에도 가짜 기록을 만들지 않고 빈 목록을 준다.
 */
interface CallHistoryRepository {
    val requiredPermission: String?
    val availability: StateFlow<CallHistoryAvailability>

    /** 최근 통화기록 (최신순). [availability] 가 Available 일 때만 내용이 있다. */
    val entries: StateFlow<List<CallLogEntry>>

    fun refresh()
    fun onPermissionResult(granted: Boolean)
}
