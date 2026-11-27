package io.github.herfar.barodial.testing

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallHistoryRepository
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.ContactsRepository
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.favorites.FavoriteEntry
import io.github.herfar.barodial.data.favorites.FavoritesRepository
import io.github.herfar.barodial.data.history.HistoryEntry
import io.github.herfar.barodial.data.history.HistoryKind
import io.github.herfar.barodial.data.history.SearchHistoryRepository
import io.github.herfar.barodial.data.places.GeoPoint
import io.github.herfar.barodial.data.places.HttpResult
import io.github.herfar.barodial.data.places.HttpTransport
import io.github.herfar.barodial.data.places.KakaoLocalClient
import io.github.herfar.barodial.data.places.LocationProvider
import io.github.herfar.barodial.data.places.NetworkMonitor
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.CallRecordingsRepository
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.data.search.ContactSearchEngine
import io.github.herfar.barodial.data.settings.AppSettings
import io.github.herfar.barodial.data.settings.SettingsRepository
import io.github.herfar.barodial.dial.DialerLauncher
import io.github.herfar.barodial.ui.MainViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.robolectric.Shadows.shadowOf

class FakeContactsRepository(initial: ContactsState) : ContactsRepository {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<ContactsState> = mutable.asStateFlow()
    var reloadCount = 0
        private set

    override fun refreshIfNeeded() = Unit
    override fun reload() {
        reloadCount++
    }

    fun set(state: ContactsState) {
        mutable.value = state
    }

    companion object {
        val engine = ContactSearchEngine()
        fun ready(contacts: List<Contact> = TestContacts.ALL) =
            FakeContactsRepository(ContactsState.Ready.build(ContactsState.Ready.sortContacts(contacts), engine))
    }
}

class InMemoryFavoritesRepository(initial: List<FavoriteEntry> = emptyList()) : FavoritesRepository {
    val list = MutableStateFlow(initial)
    val preferred = MutableStateFlow<Map<String, String>>(emptyMap())
    override val favorites: Flow<List<FavoriteEntry>> = list
    override val preferredNumbers: Flow<Map<String, String>> = preferred
    override suspend fun add(entry: FavoriteEntry) {
        if (list.value.none { it.lookupKey == entry.lookupKey }) list.value = list.value + entry
    }
    override suspend fun remove(lookupKey: String) {
        list.value = list.value.filterNot { it.lookupKey == lookupKey }
    }
    override suspend fun move(lookupKey: String, offset: Int) {
        val l = list.value.toMutableList()
        val from = l.indexOfFirst { it.lookupKey == lookupKey }
        if (from < 0) return
        l.add((from + offset).coerceIn(0, l.lastIndex), l.removeAt(from))
        list.value = l
    }
    override suspend fun setPreferredNumber(lookupKey: String, nationalDigits: String?) {
        preferred.value = if (nationalDigits == null) preferred.value - lookupKey else preferred.value + (lookupKey to nationalDigits)
    }
}

class InMemorySearchHistoryRepository(private val clock: () -> Long = System::currentTimeMillis) : SearchHistoryRepository {
    val list = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val lastDialed = MutableStateFlow<String?>(null)
    override val entries: Flow<List<HistoryEntry>> = list.map { l -> l.sortedByDescending { it.lastUsedMillis } }
    override val lastDialedNumber: Flow<String?> = lastDialed

    override suspend fun recordContact(lookupKey: String, contactId: Long, displayName: String, dialedNumber: String?) =
        record(HistoryKind.CONTACT, lookupKey, contactId, displayName, dialedNumber)

    override suspend fun recordNumber(number: String, dialed: Boolean) =
        record(HistoryKind.NUMBER, KoreanPhoneNumbers.toNationalDigits(number), -1, number, if (dialed) number else null)

    private fun record(kind: HistoryKind, key: String, id: Long, label: String, dialed: String?) {
        if (dialed != null) lastDialed.value = dialed
        val existing = list.value.firstOrNull { it.kind == kind && it.key == key }
        val e = HistoryEntry(kind, key, id, label, clock(), (existing?.dialCount ?: 0) + if (dialed != null) 1 else 0)
        list.value = listOf(e) + list.value.filterNot { it.kind == kind && it.key == key }
    }

    override suspend fun remove(kind: HistoryKind, key: String) {
        list.value = list.value.filterNot { it.kind == kind && it.key == key }
    }

    override suspend fun clear() {
        list.value = emptyList()
        lastDialed.value = null
    }
}

class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val current = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = current
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        current.value = transform(current.value)
    }
}

class FakeCallHistoryRepository(
    availability: CallHistoryAvailability = CallHistoryAvailability.PermissionRequired("android.permission.READ_CALL_LOG"),
    entries: List<CallLogEntry> = emptyList(),
    override val requiredPermission: String? = "android.permission.READ_CALL_LOG",
) : CallHistoryRepository {
    val mutableAvailability = MutableStateFlow(availability)
    val mutableEntries = MutableStateFlow(entries)
    override val availability: StateFlow<CallHistoryAvailability> = mutableAvailability
    override val entries: StateFlow<List<CallLogEntry>> = mutableEntries
    var lastPermissionResult: Boolean? = null
    override fun refresh() = Unit
    override fun onPermissionResult(granted: Boolean) {
        lastPermissionResult = granted
    }
}

class FakeCallRecordingsRepository(
    availability: RecordingsAvailability = RecordingsAvailability.PermissionRequired("android.permission.READ_MEDIA_AUDIO"),
    recordings: List<CallRecording> = emptyList(),
) : CallRecordingsRepository {
    override val requiredPermission: String = "android.permission.READ_MEDIA_AUDIO"
    val mutableAvailability = MutableStateFlow(availability)
    val mutableRecordings = MutableStateFlow(recordings)
    override val availability: StateFlow<RecordingsAvailability> = mutableAvailability
    override val recordings: StateFlow<List<CallRecording>> = mutableRecordings
    var lastPermissionResult: Boolean? = null
    var rescanCount = 0
    override fun refresh() = Unit
    override fun rescan() {
        rescanCount++
    }
    override fun onPermissionResult(granted: Boolean) {
        lastPermissionResult = granted
        if (granted) mutableAvailability.value = RecordingsAvailability.Available
    }
}

/** 카카오 API 대신 응답하는 가짜 HTTP. 기본은 빈 결과. */
class FakePlaceTransport : HttpTransport {
    val requests = mutableListOf<Pair<String, Map<String, String>>>()
    var handler: suspend (url: String) -> HttpResult = { HttpResult(200, KakaoSamples.page()) }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
        requests += url to headers
        return handler(url)
    }

    /** 보낸 검색어 목록 */
    fun queries(): List<String?> = requests.map { android.net.Uri.parse(it.first).getQueryParameter("query") }

    /** 검색어별 응답 지정 (없으면 빈 결과) */
    fun respond(byQuery: Map<String, String>) {
        handler = { url -> HttpResult(200, byQuery[android.net.Uri.parse(url).getQueryParameter("query")] ?: KakaoSamples.page()) }
    }
}

class FakeLocationProvider(var granted: Boolean = false, var point: GeoPoint? = GeoPoint(37.6152, 126.7156)) : LocationProvider {
    override val permission: String = "android.permission.ACCESS_COARSE_LOCATION"
    override fun hasPermission(): Boolean = granted
    override suspend fun currentLocation(): GeoPoint? = if (granted) point else null
}

class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}

/** 가짜 저장소로 ViewModel 을 만드는 도우미 */
class Harness(
    context: Context,
    val contacts: FakeContactsRepository = FakeContactsRepository.ready(),
    val favorites: InMemoryFavoritesRepository = InMemoryFavoritesRepository(),
    val history: InMemorySearchHistoryRepository = InMemorySearchHistoryRepository(),
    val settings: InMemorySettingsRepository = InMemorySettingsRepository(),
    val callHistory: FakeCallHistoryRepository = FakeCallHistoryRepository(),
    val recordings: FakeCallRecordingsRepository = FakeCallRecordingsRepository(),
    val placeTransport: FakePlaceTransport = FakePlaceTransport(),
    val location: FakeLocationProvider = FakeLocationProvider(),
    val network: FakeNetworkMonitor = FakeNetworkMonitor(),
    /** UI 테스트에서는 기다림 없이 바로 검색한다 (디바운스는 OnlinePlaceSearchTest 에서 가상 시간으로 확인) */
    onlineDebounceMillis: Long = 0L,
) {
    val launcher = DialerLauncher(context)
    val viewModel = MainViewModel(
        contactsRepository = contacts,
        favoritesRepository = favorites,
        historyRepository = history,
        settingsRepository = settings,
        callHistoryRepository = callHistory,
        recordingsRepository = recordings,
        dialer = launcher,
        engine = FakeContactsRepository.engine,
        savedState = SavedStateHandle(),
        placeClient = KakaoLocalClient(placeTransport),
        locationProvider = location,
        networkMonitor = network,
        onlineDebounceMillis = onlineDebounceMillis,
    )
}

/** 메인 루퍼와 스냅샷 알림을 돌리면서 조건이 참이 될 때까지 기다린다. */
fun awaitUntil(timeoutMillis: Long = 10_000, describe: () -> String = { "" }, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        if (System.currentTimeMillis() > deadline) throw AssertionError("조건이 시간 안에 충족되지 않았습니다 ${describe()}")
        Thread.sleep(10)
    }
}
