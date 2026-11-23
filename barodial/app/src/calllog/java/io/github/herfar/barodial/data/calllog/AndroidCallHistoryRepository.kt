package io.github.herfar.barodial.data.calllog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CallLog Provider 를 읽는다 (calllog 실험 빌드 전용).
 *
 * 이 구현은 권한이 실제로 허용된 경우에만 기록을 읽는다.
 * 권한을 우회하거나 다른 앱의 화면을 읽는 방식은 사용하지 않는다.
 */
class AndroidCallHistoryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val limit: Int = 5_000,
) : CallHistoryRepository {

    override val requiredPermission: String = Manifest.permission.READ_CALL_LOG

    private val _availability = MutableStateFlow(currentAvailability(requestedBefore = false))
    override val availability: StateFlow<CallHistoryAvailability> = _availability.asStateFlow()

    private val _entries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    override val entries: StateFlow<List<CallLogEntry>> = _entries.asStateFlow()

    private var requestedBefore = false
    private var observerRegistered = false
    private var job: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = load(delayMillis = 500)
    }

    init {
        load(0)
    }

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED

    private fun currentAvailability(requestedBefore: Boolean): CallHistoryAvailability = when {
        granted() -> CallHistoryAvailability.Available
        requestedBefore -> CallHistoryAvailability.Denied(requiredPermission)
        else -> CallHistoryAvailability.PermissionRequired(requiredPermission)
    }

    override fun refresh() = load(0)

    override fun onPermissionResult(granted: Boolean) {
        requestedBefore = true
        load(0)
    }

    private fun load(delayMillis: Long) {
        job?.cancel()
        job = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val availability = currentAvailability(requestedBefore)
            _availability.value = availability
            if (availability != CallHistoryAvailability.Available) {
                _entries.value = emptyList()
                return@launch
            }
            registerObserver()
            try {
                _entries.value = withContext(Dispatchers.IO) { query() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _availability.value = CallHistoryAvailability.Denied(requiredPermission)
                _entries.value = emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "통화기록을 읽지 못했습니다", e)
            }
        }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        try {
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            observerRegistered = true
        } catch (e: SecurityException) {
            Log.w(TAG, "통화기록 변경 감지를 등록하지 못했습니다", e)
        }
    }

    private fun query(): List<CallLogEntry> {
        // 개수 제한(limit) 매개변수는 쓰지 않는다. 일부 제조사 Provider 는 제한을 정렬보다 먼저 적용해
        // 오래된 기록만 돌려줄 수 있으므로, 전부 읽은 뒤 앱에서 최신순으로 정렬하고 자른다.
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val result = ArrayList<CallLogEntry>()
        context.contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val iNumber = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val iDuration = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val number = c.getString(iNumber).orEmpty()
                result += CallLogEntry(
                    id = c.getLong(iId),
                    number = number,
                    nationalDigits = KoreanPhoneNumbers.toNationalDigits(number),
                    cachedName = c.getString(iName)?.takeIf { it.isNotBlank() },
                    type = mapType(c.getInt(iType)),
                    timestampMillis = c.getLong(iDate),
                    durationSeconds = c.getLong(iDuration),
                )
            }
        }
        return result.sortedByDescending { it.timestampMillis }.take(limit)
    }

    private fun mapType(type: Int): CallType = when (type) {
        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.OTHER
    }

    private companion object {
        const val TAG = "CallHistoryRepository"
    }
}
