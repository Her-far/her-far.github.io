package io.github.herfar.barodial.data.calllog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL, OTHER }

enum class CallFilter { ALL, MISSED, INCOMING, OUTGOING }

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
    /** 이 빌드에는 통화기록 기능이 없다 (기본 standard 빌드: READ_CALL_LOG 미선언). */
    data object NotIncludedInBuild : CallHistoryAvailability

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
 * 통화기록 공급원. 권한 정책 때문에 빌드 변형(flavor)마다 구현이 다르다.
 *  - standard: [UnavailableCallHistoryRepository]
 *  - calllog : CallLog Provider 를 읽는 구현 (src/calllog)
 *
 * 앱의 나머지 부분은 이 인터페이스만 사용하므로, 향후 권한이 확보되면 구현만 교체하면 된다.
 */
interface CallHistoryRepository {
    val requiredPermission: String?
    val availability: StateFlow<CallHistoryAvailability>

    /** 최근 통화기록 (최신순). [availability] 가 Available 일 때만 내용이 있다. */
    val entries: StateFlow<List<CallLogEntry>>

    fun refresh()
    fun onPermissionResult(granted: Boolean)
}

/** 통화기록을 읽지 않는 구현. 어떤 경우에도 가짜 기록을 만들지 않는다. */
object UnavailableCallHistoryRepository : CallHistoryRepository {
    override val requiredPermission: String? = null
    override val availability: StateFlow<CallHistoryAvailability> =
        MutableStateFlow<CallHistoryAvailability>(CallHistoryAvailability.NotIncludedInBuild).asStateFlow()
    override val entries: StateFlow<List<CallLogEntry>> = MutableStateFlow<List<CallLogEntry>>(emptyList()).asStateFlow()
    override fun refresh() = Unit
    override fun onPermissionResult(granted: Boolean) = Unit
}
