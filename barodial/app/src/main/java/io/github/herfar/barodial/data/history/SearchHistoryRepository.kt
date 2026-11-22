package io.github.herfar.barodial.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.core.util.ListCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HistoryKind { CONTACT, NUMBER }

/**
 * 이 앱 안에서 검색해 열어 본 연락처, 또는 이 앱에서 발신 요청한 번호의 기록.
 * 실제 통화기록(CallLog)이 아니며 기기에만 저장된다.
 *
 * @param key CONTACT: lookupKey, NUMBER: 국내 숫자 형식 번호
 * @param dialCount 이 앱에서 발신 요청한 횟수 ("자주 거는 연락처" 정렬용)
 */
data class HistoryEntry(
    val kind: HistoryKind,
    val key: String,
    val contactId: Long,
    val label: String,
    val lastUsedMillis: Long,
    val dialCount: Int,
)

interface SearchHistoryRepository {
    /** 최근 사용 순 */
    val entries: Flow<List<HistoryEntry>>

    /** 이 앱에서 마지막으로 발신 요청한 번호 (키패드에서 빈 상태로 통화 버튼을 누르면 채워 준다) */
    val lastDialedNumber: Flow<String?>

    /** @param dialedNumber 이 연락처로 발신 요청했다면 그 번호, 단순히 열어 본 것이면 null */
    suspend fun recordContact(lookupKey: String, contactId: Long, displayName: String, dialedNumber: String?)
    suspend fun recordNumber(number: String, dialed: Boolean)
    suspend fun remove(kind: HistoryKind, key: String)
    suspend fun clear()
}

class DataStoreSearchHistoryRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 100,
) : SearchHistoryRepository {

    private val keyHistory = stringPreferencesKey("search_history_v1")
    private val keyLastDialed = stringPreferencesKey("last_dialed_v1")

    override val entries: Flow<List<HistoryEntry>> = dataStore.data.map { decode(it[keyHistory]) }

    override val lastDialedNumber: Flow<String?> = dataStore.data.map { it[keyLastDialed] }

    override suspend fun recordContact(lookupKey: String, contactId: Long, displayName: String, dialedNumber: String?) =
        record(HistoryKind.CONTACT, lookupKey, contactId, displayName, dialedNumber)

    override suspend fun recordNumber(number: String, dialed: Boolean) {
        val digits = KoreanPhoneNumbers.toNationalDigits(number)
        if (digits.isEmpty()) return
        record(HistoryKind.NUMBER, digits, -1L, number, if (dialed) number else null)
    }

    private suspend fun record(kind: HistoryKind, key: String, contactId: Long, label: String, dialedNumber: String?) = edit(dialedNumber) { list ->
        val existing = list.firstOrNull { it.kind == kind && it.key == key }
        val updated = HistoryEntry(
            kind = kind,
            key = key,
            contactId = contactId,
            label = label,
            lastUsedMillis = clock(),
            dialCount = (existing?.dialCount ?: 0) + if (dialedNumber != null) 1 else 0,
        )
        (listOf(updated) + list.filterNot { it.kind == kind && it.key == key }).take(maxEntries)
    }

    override suspend fun remove(kind: HistoryKind, key: String) = edit { list ->
        list.filterNot { it.kind == kind && it.key == key }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(keyHistory)
            it.remove(keyLastDialed)
        }
    }

    private suspend fun edit(dialedNumber: String? = null, transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        dataStore.edit { prefs ->
            if (dialedNumber != null) prefs[keyLastDialed] = dialedNumber
            val next = transform(decode(prefs[keyHistory]))
            prefs[keyHistory] = ListCodec.encode(
                next.map { listOf(it.kind.name, it.key, it.contactId.toString(), it.label, it.lastUsedMillis.toString(), it.dialCount.toString()) },
            )
        }
    }

    private fun decode(text: String?): List<HistoryEntry> =
        ListCodec.decode(text).mapNotNull { r ->
            if (r.size < 6) return@mapNotNull null
            val kind = HistoryKind.entries.firstOrNull { it.name == r[0] } ?: return@mapNotNull null
            HistoryEntry(kind, r[1], r[2].toLongOrNull() ?: -1L, r[3], r[4].toLongOrNull() ?: 0L, r[5].toIntOrNull() ?: 0)
        }.sortedByDescending { it.lastUsedMillis }
}
