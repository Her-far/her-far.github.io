package io.github.herfar.barodial.ui

import android.content.Intent
import androidx.annotation.StringRes
import io.github.herfar.barodial.data.calllog.CallDaySection
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.history.HistoryEntry
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.search.SearchResult

/** 하단 탭 (좌우 스와이프 순서와 같다) */
enum class Destination { HOME, CONTACTS, RECENTS, FAVORITES }

/**
 * 탭 위에 쌓이는 화면. 접힌 화면에서는 전체 화면, 펼친 화면에서는 오른쪽 창에 표시된다.
 * 키패드는 홈 탭(접힘) 또는 오른쪽 창의 기본 화면(펼침)이므로 별도 경로가 없다.
 */
sealed interface Route {
    data class Detail(val lookupKey: String, val contactId: Long) : Route

    /** 연락처에 없는 번호의 상세 (통화내역·녹음) */
    data class NumberDetail(val number: String) : Route
    data object Settings : Route

    /** 통화 녹음 연결 진단 */
    data object RecordingDiagnostics : Route

    /** 온라인 상호·기관 검색 설정 (키·프록시·설정 방법) */
    data object OnlineSearchSetup : Route
}

/** 검색 결과 목록의 한 줄. 리스트 index 0 이 가장 관련도가 높다. */
sealed interface SearchRow {
    val key: String

    /** 입력한 숫자로 바로 전화 */
    data class DialNumber(val number: String, val contact: Contact?) : SearchRow {
        override val key: String get() = "dial:$number"
    }

    data class ContactHit(val result: SearchResult) : SearchRow {
        override val key: String get() = "contact:${result.contact.id}"
    }

    /** 연락처에 없는 번호 중 이 앱에서 최근 검색·발신한 번호 */
    data class RecentNumber(val entry: HistoryEntry, val contact: Contact?, val digitRange: IntRange?) : SearchRow {
        override val key: String get() = "recentNumber:${entry.key}"
    }

    /** 검색어가 비어 있을 때 보여 주는 최근 검색 연락처 */
    data class RecentContact(val entry: HistoryEntry, val contact: Contact) : SearchRow {
        override val key: String get() = "recentContact:${entry.key}"
    }

    /** 통화기록에 있지만 연락처에 없는 번호 (통화기록 권한이 있을 때만) */
    data class CallLogNumber(val entry: CallLogEntry, val digitRange: IntRange?) : SearchRow {
        override val key: String get() = "calllog:${entry.nationalDigits}"
    }

    data object ClearHistory : SearchRow {
        override val key: String get() = "clearHistory"
    }

    data object ContactsPermissionHint : SearchRow {
        override val key: String get() = "permissionHint"
    }
}

data class SearchUiState(
    val query: String = "",
    val rows: List<SearchRow> = emptyList(),
    val contactResultCount: Int = 0,
    val contactsPermissionMissing: Boolean = false,
)

data class FavoriteItem(
    val contact: Contact,
    /** 시스템 연락처 앱의 즐겨찾기(STARRED)에서 온 항목 */
    val fromSystem: Boolean,
)

data class HomeUiState(
    val favorites: List<FavoriteItem> = emptyList(),
    val frequent: List<Contact> = emptyList(),
)

data class FavoritesUiState(
    val app: List<FavoriteItem> = emptyList(),
    val system: List<FavoriteItem> = emptyList(),
)

data class DialerUiState(
    val digits: String = "",
    val formatted: String = "",
    val matches: List<SearchResult> = emptyList(),
)

data class RecentsUiState(
    val sections: List<CallDaySection> = emptyList(),
    /** 국내 숫자 형식 번호 → 연락처 */
    val contactsByNumber: Map<String, Contact> = emptyMap(),
    /** 통화기록 id → 연결된 통화 녹음 */
    val recordingByCallId: Map<Long, CallRecording> = emptyMap(),
    /** 통화 녹음 파일 목록 (최신순, 파일 이름으로 찾은 연락처 포함) */
    val recordings: List<RecordingItem> = emptyList(),
)

/** 녹음 파일과, 파일 이름으로 찾은 연락처 */
data class RecordingItem(val recording: CallRecording, val contact: Contact?)

/** 지금 재생 창에 열린 녹음 */
data class PlayingRecording(val recording: CallRecording, val title: String)

data class NumberPickerState(val contact: Contact)

enum class PermissionPurpose { CONTACTS, CALL_PHONE, CALL_LOG, RECORDINGS, LOCATION }

/** 설정 화면의 온라인 검색 [연결 확인] 결과 */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Running : ConnectionTestState

    /** 키·주소 형식이 틀려 요청하지 않았다 */
    data object Invalid : ConnectionTestState
    data class Success(val totalCount: Int) : ConnectionTestState
    data class Failed(val error: io.github.herfar.barodial.data.places.PlaceSearchError) : ConnectionTestState
}

/** ViewModel → UI 일회성 이벤트 */
sealed interface UiEffect {
    /** 후보 인텐트를 순서대로 실행해 처음 성공한 것을 쓴다. 모두 실패하면 [failureMessage] 표시 */
    data class Launch(val intents: List<Intent>, @param:StringRes val failureMessage: Int) : UiEffect

    /**
     * 시스템 연락처 앱의 등록·편집 화면을 결과를 받는 방식으로 연다.
     * 저장하거나 취소하고 돌아오면 [MainViewModel.onContactEditorClosed] 가 불린다.
     * 후보 인텐트를 순서대로 시도하고, 모두 실패하면 [failureMessage] 를 보여 준다.
     */
    data class EditContact(val intents: List<Intent>, @param:StringRes val failureMessage: Int) : UiEffect

    data class Message(@param:StringRes val message: Int, val arg: String? = null) : UiEffect

    data class RequestPermission(val permission: String, val purpose: PermissionPurpose) : UiEffect

    /** 키보드 숨기기 (상세 화면으로 이동할 때 등) */
    data object HideKeyboard : UiEffect
}
