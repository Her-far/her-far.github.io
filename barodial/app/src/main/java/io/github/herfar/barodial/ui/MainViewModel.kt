package io.github.herfar.barodial.ui

import android.Manifest
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.herfar.barodial.BaroDialApplication
import io.github.herfar.barodial.R
import io.github.herfar.barodial.core.hangul.SearchText
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.calllog.CallFilter
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallHistoryRepository
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallLogGrouping
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.ContactsRepository
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.contacts.PhoneNumber
import io.github.herfar.barodial.data.favorites.FavoriteEntry
import io.github.herfar.barodial.data.favorites.FavoritesRepository
import io.github.herfar.barodial.data.history.HistoryEntry
import io.github.herfar.barodial.data.history.HistoryKind
import io.github.herfar.barodial.data.history.SearchHistoryRepository
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.CallRecordingsRepository
import io.github.herfar.barodial.data.recordings.RecordingMatcher
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.data.search.ContactSearchEngine
import io.github.herfar.barodial.data.settings.AppSettings
import io.github.herfar.barodial.data.settings.SettingsRepository
import io.github.herfar.barodial.data.settings.StartTab
import io.github.herfar.barodial.dial.DefaultDialerInfo
import io.github.herfar.barodial.dial.DialerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * 앱 전체 화면 상태와 동작.
 * 화면 상태는 ViewModel 에 있으므로 폴더블 접기/펼치기, 회전, 분할화면 전환 후에도 유지된다.
 */
class MainViewModel(
    private val contactsRepository: ContactsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: SearchHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val callHistoryRepository: CallHistoryRepository,
    private val recordingsRepository: CallRecordingsRepository,
    private val dialer: DialerLauncher,
    private val engine: ContactSearchEngine,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // ---------------------------------------------------------------- 데이터

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val contactsState: StateFlow<ContactsState> = contactsRepository.state
    val callHistoryAvailability: StateFlow<CallHistoryAvailability> = callHistoryRepository.availability
    val callLogEntries: StateFlow<List<CallLogEntry>> = callHistoryRepository.entries
    val callHistoryPermission: String? get() = callHistoryRepository.requiredPermission
    val recordingsAvailability: StateFlow<RecordingsAvailability> = recordingsRepository.availability
    val recordings: StateFlow<List<CallRecording>> = recordingsRepository.recordings

    val favoriteEntries: StateFlow<List<FavoriteEntry>> =
        favoritesRepository.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preferredNumbers: StateFlow<Map<String, String>> =
        favoritesRepository.preferredNumbers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val historyEntries: StateFlow<List<HistoryEntry>> =
        historyRepository.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val lastDialedNumber: StateFlow<String?> =
        historyRepository.lastDialedNumber.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------------------------------------------------------------- 화면 상태

    var destination by mutableStateOf(
        savedState.get<String>(KEY_DEST)?.let { v -> Destination.entries.firstOrNull { it.name == v } } ?: Destination.HOME,
    )
        private set

    /** 탭 위에 쌓인 화면들 (비어 있으면 탭 화면) */
    val backStack: SnapshotStateList<Route> = mutableStateListOf()

    var searchActive by mutableStateOf(savedState.get<Boolean>(KEY_SEARCH_ACTIVE) ?: false)
        private set

    /** 값이 바뀔 때마다 검색창에 포커스를 주고 키보드를 연다. */
    var searchFocusRequest by mutableIntStateOf(0)
        private set

    /** 하단 검색창 입력 상태. 한글 조합 중인 글자도 포함된다. */
    val searchField = TextFieldState(savedState.get<String>(KEY_QUERY).orEmpty())

    private val _dialDigits = MutableStateFlow(savedState.get<String>(KEY_DIAL).orEmpty())
    val dialDigits: StateFlow<String> = _dialDigits.asStateFlow()

    var favoritesEditMode by mutableStateOf(false)
        private set

    private val _callFilter = MutableStateFlow(CallFilter.ALL)
    val callFilter: StateFlow<CallFilter> = _callFilter.asStateFlow()

    var numberPicker by mutableStateOf<NumberPickerState?>(null)
        private set

    var dialerInfo by mutableStateOf<DefaultDialerInfo?>(null)
        private set

    var canOpenCallLogScreen by mutableStateOf(false)
        private set

    /** 재생 창에 열린 통화 녹음 */
    var playing by mutableStateOf<PlayingRecording?>(null)
        private set

    private var pendingDirectCallEnable = false
    private var destinationChosen = savedState.get<String>(KEY_DEST) != null

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private val queryFlow: Flow<String> = snapshotFlow { searchField.text.toString() }.distinctUntilChanged()

    // ---------------------------------------------------------------- 파생 상태

    val searchUi: StateFlow<SearchUiState> = combine(
        queryFlow,
        contactsState,
        combine(favoriteEntries, historyEntries) { f, h -> f to h },
        callLogEntries,
    ) { q, cs, (favorites, history), calls -> SearchInputs(q, cs, favorites, history, calls) }
        // 검색어(snapshotFlow)는 입력이 일어나는 메인 스레드에서 관찰하고, 검색 계산만 백그라운드에서 한다.
        // (관찰을 백그라운드에서 시작하면 시작 직후의 입력 변경을 놓칠 수 있다)
        .mapLatest { input -> withContext(Dispatchers.Default) { buildSearch(input) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())

    val homeUi: StateFlow<HomeUiState> = combine(contactsState, favoriteEntries, historyEntries, settings) { cs, f, h, s ->
        buildHome(cs, f, h, s)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val favoritesUi: StateFlow<FavoritesUiState> = combine(contactsState, favoriteEntries, settings) { cs, f, s ->
        val (app, system) = resolveFavorites(cs, f, s.showSystemFavorites)
        FavoritesUiState(app, system)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, FavoritesUiState())

    val dialerUi: StateFlow<DialerUiState> = combine(_dialDigits, contactsState) { d, cs -> d to cs }
        .mapLatest { (d, cs) ->
            val ready = cs as? ContactsState.Ready
            val digitsOnly = KoreanPhoneNumbers.digitsOnly(d)
            DialerUiState(
                digits = d,
                formatted = KoreanPhoneNumbers.format(d),
                matches = if (ready != null && digitsOnly.isNotEmpty()) engine.searchByNumber(ready.index, digitsOnly, limit = 30) else emptyList(),
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DialerUiState())

    val recentsUi: StateFlow<RecentsUiState> = combine(callLogEntries, _callFilter, contactsState, recordings) { entries, filter, cs, recs ->
        buildRecents(entries, filter, cs, recs)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, RecentsUiState())

    val favoriteKeys: StateFlow<Set<String>> = favoriteEntries
        .map { list -> list.mapTo(HashSet()) { it.lookupKey } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (!destinationChosen) destination = s.startTab.toDestination()
        }
        viewModelScope.launch { queryFlow.collect { savedState[KEY_QUERY] = it } }
        viewModelScope.launch { _dialDigits.collect { savedState[KEY_DIAL] = it } }
    }

    // ---------------------------------------------------------------- 생명주기

    fun onResume() {
        contactsRepository.refreshIfNeeded()
        callHistoryRepository.refresh()
        recordingsRepository.refresh()
        dialerInfo = dialer.defaultDialerInfo()
        canOpenCallLogScreen = dialer.canOpenCallLogScreen()
        if (settings.value.directCall && !dialer.hasCallPermission()) {
            viewModelScope.launch { settingsRepository.update { it.copy(directCall = false) } }
        }
    }

    // ---------------------------------------------------------------- 탐색

    fun selectDestination(d: Destination) {
        destinationChosen = true
        if (searchActive) exitSearch()
        favoritesEditMode = false
        if (d != Destination.RECENTS) _callFilter.value = CallFilter.ALL
        backStack.removeAll { it is Route.Settings || it is Route.RecordingDiagnostics }
        destination = d
        savedState[KEY_DEST] = d.name
    }

    /** 좌우 스와이프로 탭이 바뀌었을 때 */
    fun onPageSettled(d: Destination) {
        if (d == destination) return
        destinationChosen = true
        favoritesEditMode = false
        // 최근기록을 벗어나면 필터(녹음·부재중 등)를 '전체'로 되돌린다
        if (d != Destination.RECENTS) _callFilter.value = CallFilter.ALL
        destination = d
        savedState[KEY_DEST] = d.name
    }

    val canHandleBack: Boolean
        get() = playing != null || numberPicker != null || searchActive || backStack.isNotEmpty() || favoritesEditMode ||
            destination != settings.value.startTab.toDestination()

    /** @return 처리했으면 true. false 면 앱을 닫는다. */
    fun onBack(): Boolean {
        when {
            playing != null -> playing = null
            numberPicker != null -> numberPicker = null
            backStack.isNotEmpty() -> backStack.removeAt(backStack.lastIndex)
            searchActive -> exitSearch()
            favoritesEditMode -> favoritesEditMode = false
            destination != settings.value.startTab.toDestination() -> selectDestination(settings.value.startTab.toDestination())
            else -> return false
        }
        return true
    }

    fun popRoute() {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    private fun push(route: Route) {
        val top = backStack.lastOrNull()
        when {
            top == route -> return
            // 상세 → 상세는 쌓지 않고 바꾼다 (펼친 화면 오른쪽 창에서 연락처를 연달아 볼 때)
            top.isDetail() && route.isDetail() -> backStack[backStack.lastIndex] = route
            else -> {
                backStack.remove(route)
                backStack.add(route)
            }
        }
    }

    private fun Route?.isDetail() = this is Route.Detail || this is Route.NumberDetail

    fun openContact(contact: Contact) {
        if (searchActive && settings.value.saveSearchHistory) {
            viewModelScope.launch { historyRepository.recordContact(contact.lookupKey, contact.id, contact.displayName, null) }
        }
        sendEffect(UiEffect.HideKeyboard)
        push(Route.Detail(contact.lookupKey, contact.id))
    }

    /**
     * 키패드로 이동한다. 접힌 화면에서는 홈 탭(다이얼), 펼친 화면에서는 오른쪽 창의 키패드가 보인다.
     */
    fun openKeypad(prefill: String? = null) {
        if (prefill != null) setDialDigits(KoreanPhoneNumbers.sanitizeDialInput(prefill))
        if (searchActive) exitSearch()
        sendEffect(UiEffect.HideKeyboard)
        backStack.clear()
        selectDestination(Destination.HOME)
    }

    /** 펼친 화면: 오른쪽 창을 키패드로 되돌린다. */
    fun showKeypadPane() {
        sendEffect(UiEffect.HideKeyboard)
        backStack.clear()
    }

    /** 연락처에 없는 번호의 통화내역·녹음 화면 */
    fun openNumber(number: String) {
        if (number.isBlank()) return
        val contact = (contactsState.value as? ContactsState.Ready)?.findByNumber(KoreanPhoneNumbers.toNationalDigits(number))
        if (contact != null) {
            openContact(contact)
            return
        }
        sendEffect(UiEffect.HideKeyboard)
        push(Route.NumberDetail(number))
    }

    fun openRecordingDiagnostics() = push(Route.RecordingDiagnostics)

    fun openSettings() {
        sendEffect(UiEffect.HideKeyboard)
        push(Route.Settings)
    }

    // ---------------------------------------------------------------- 검색

    fun onSearchFocused() {
        if (!searchActive) {
            searchActive = true
            savedState[KEY_SEARCH_ACTIVE] = true
        }
        favoritesEditMode = false
    }

    /** 바로가기·위젯 등에서 바로 검색 시작 */
    fun startSearch() {
        backStack.clear()
        onSearchFocused()
        searchFocusRequest++
    }

    fun exitSearch() {
        searchActive = false
        savedState[KEY_SEARCH_ACTIVE] = false
        searchField.clearText()
        sendEffect(UiEffect.HideKeyboard)
    }

    fun removeHistory(entry: HistoryEntry) {
        viewModelScope.launch { historyRepository.remove(entry.kind, entry.key) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clear() }
        sendEffect(UiEffect.Message(R.string.message_history_cleared))
    }

    private data class SearchInputs(
        val query: String,
        val contacts: ContactsState,
        val favorites: List<FavoriteEntry>,
        val history: List<HistoryEntry>,
        val calls: List<CallLogEntry>,
    )

    private fun buildSearch(input: SearchInputs): SearchUiState {
        val query = input.query.trim()
        val ready = input.contacts as? ContactsState.Ready
        val permissionMissing = input.contacts is ContactsState.PermissionRequired
        val rows = ArrayList<SearchRow>()

        if (query.isEmpty()) {
            if (permissionMissing) rows += SearchRow.ContactsPermissionHint
            for (h in input.history.take(MAX_RECENT_IN_SEARCH)) {
                when (h.kind) {
                    HistoryKind.CONTACT -> ready?.find(h.key, h.contactId)?.let { rows += SearchRow.RecentContact(h, it) }
                    HistoryKind.NUMBER -> rows += SearchRow.RecentNumber(h, ready?.findByNumber(h.key), null)
                }
            }
            if (input.history.isNotEmpty()) rows += SearchRow.ClearHistory
            return SearchUiState(query = "", rows = rows, contactsPermissionMissing = permissionMissing)
        }

        val numeric = KoreanPhoneNumbers.looksLikePhoneQuery(query)
        val digits = if (numeric) KoreanPhoneNumbers.toNationalDigits(query) else ""
        if (numeric) {
            val dialable = KoreanPhoneNumbers.sanitizeDialInput(query)
            if (dialable.isNotEmpty()) rows += SearchRow.DialNumber(dialable, ready?.findByNumber(digits))
        }

        val favoriteKeys = input.favorites.mapTo(HashSet()) { it.lookupKey }
        val dialCounts = input.history.filter { it.kind == HistoryKind.CONTACT }.associate { it.key to it.dialCount }
        val results = if (ready != null) {
            engine.search(ready.index, query) { c ->
                (if (c.lookupKey in favoriteKeys) 3 else 0) + (if (c.starred) 2 else 0) + min(dialCounts[c.lookupKey] ?: 0, 10)
            }
        } else {
            emptyList()
        }
        results.mapTo(rows) { SearchRow.ContactHit(it) }

        if (numeric && digits.isNotEmpty()) {
            val seen = HashSet<String>()
            for (h in input.history) {
                if (h.kind != HistoryKind.NUMBER || ready?.findByNumber(h.key) != null) continue
                val range = matchDigits(h.key, digits) ?: continue
                if (seen.add(h.key)) rows += SearchRow.RecentNumber(h, null, range)
            }
            for (c in input.calls) {
                if (c.nationalDigits.isEmpty() || ready?.findByNumber(c.nationalDigits) != null) continue
                val range = matchDigits(c.nationalDigits, digits) ?: continue
                if (seen.add(c.nationalDigits)) rows += SearchRow.CallLogNumber(c, range)
            }
        }
        if (permissionMissing && !numeric) rows += SearchRow.ContactsPermissionHint

        return SearchUiState(query, rows, results.size, permissionMissing)
    }

    private fun matchDigits(target: String, query: String): IntRange? {
        if (target.startsWith(query)) return 0 until query.length
        if (query.length >= ContactSearchEngine.MIN_INFIX_DIGITS) {
            val i = target.indexOf(query)
            if (i >= 0) return i until i + query.length
        }
        return null
    }

    // ---------------------------------------------------------------- 홈 / 즐겨찾기

    private fun resolveFavorites(
        cs: ContactsState,
        entries: List<FavoriteEntry>,
        includeSystem: Boolean,
    ): Pair<List<FavoriteItem>, List<FavoriteItem>> {
        val ready = cs as? ContactsState.Ready ?: return emptyList<FavoriteItem>() to emptyList()
        val app = entries.mapNotNull { e -> ready.find(e.lookupKey, e.contactId)?.let { FavoriteItem(it, fromSystem = false) } }
            .distinctBy { it.contact.id }
        val appIds = app.mapTo(HashSet()) { it.contact.id }
        val system = if (includeSystem) {
            ready.contacts.filter { it.starred && it.id !in appIds }.map { FavoriteItem(it, fromSystem = true) }
        } else {
            emptyList()
        }
        return app to system
    }

    private fun buildHome(cs: ContactsState, favorites: List<FavoriteEntry>, history: List<HistoryEntry>, s: AppSettings): HomeUiState {
        val ready = cs as? ContactsState.Ready
        val (app, system) = resolveFavorites(cs, favorites, s.showSystemFavorites)
        val allFavorites = app + system
        val favoriteIds = allFavorites.mapTo(HashSet()) { it.contact.id }
        val frequent = if (ready == null) {
            emptyList()
        } else {
            history.asSequence()
                .filter { it.kind == HistoryKind.CONTACT && it.dialCount >= 2 }
                .sortedByDescending { it.dialCount }
                .mapNotNull { ready.find(it.key, it.contactId) }
                .filter { it.id !in favoriteIds }
                .distinctBy { it.id }
                .take(MAX_FREQUENT)
                .toList()
        }
        return HomeUiState(allFavorites, frequent)
    }

    fun isFavorite(contact: Contact): Boolean =
        favoriteEntries.value.any { it.lookupKey == contact.lookupKey || it.contactId == contact.id }

    fun toggleFavorite(contact: Contact) {
        val existing = favoriteEntries.value.firstOrNull { it.lookupKey == contact.lookupKey || it.contactId == contact.id }
        viewModelScope.launch {
            if (existing != null) {
                favoritesRepository.remove(existing.lookupKey)
            } else {
                favoritesRepository.add(FavoriteEntry(contact.lookupKey, contact.id, contact.displayName))
            }
        }
        sendEffect(UiEffect.Message(if (existing != null) R.string.message_favorite_removed else R.string.message_favorite_added, contact.displayName))
    }

    fun toggleFavoritesEditMode() {
        favoritesEditMode = !favoritesEditMode
    }

    fun moveFavorite(contact: Contact, offset: Int) {
        val entry = favoriteEntries.value.firstOrNull { it.lookupKey == contact.lookupKey || it.contactId == contact.id } ?: return
        viewModelScope.launch { favoritesRepository.move(entry.lookupKey, offset) }
    }

    // ---------------------------------------------------------------- 전화 걸기

    /** 연락처의 기본으로 걸 번호. 정할 수 없으면 null (번호 선택 창을 띄운다) */
    fun defaultPhoneFor(contact: Contact): PhoneNumber? {
        preferredNumbers.value[contact.lookupKey]?.let { d -> contact.phoneByDigits(d)?.let { return it } }
        if (contact.phones.size == 1) return contact.phones.first()
        return contact.phones.firstOrNull { it.isDefault }
    }

    fun preferredDigitsFor(contact: Contact): String? = preferredNumbers.value[contact.lookupKey]

    fun callContact(contact: Contact, phone: PhoneNumber? = null) {
        val target = phone ?: defaultPhoneFor(contact)
        if (target == null) {
            sendEffect(UiEffect.HideKeyboard)
            numberPicker = NumberPickerState(contact)
            return
        }
        placeCall(target.number, contact)
    }

    fun onNumberPicked(contact: Contact, phone: PhoneNumber, remember: Boolean) {
        numberPicker = null
        if (remember) viewModelScope.launch { favoritesRepository.setPreferredNumber(contact.lookupKey, phone.nationalDigits) }
        placeCall(phone.number, contact)
    }

    fun setPreferredNumber(contact: Contact, phone: PhoneNumber?) {
        viewModelScope.launch { favoritesRepository.setPreferredNumber(contact.lookupKey, phone?.nationalDigits) }
    }

    fun dismissNumberPicker() {
        numberPicker = null
    }

    fun callNumber(number: String) {
        val clean = KoreanPhoneNumbers.sanitizeDialInput(number)
        if (clean.isEmpty()) return
        val contact = (contactsState.value as? ContactsState.Ready)?.findByNumber(KoreanPhoneNumbers.toNationalDigits(clean))
        placeCall(clean, contact)
    }

    private fun placeCall(number: String, contact: Contact?) {
        val s = settings.value
        val direct = dialer.canCallDirectly(number, s.directCall)
        sendEffect(UiEffect.Launch(listOf(dialer.callIntent(number, direct)), R.string.error_no_dialer))
        viewModelScope.launch {
            if (s.saveSearchHistory) {
                if (contact != null) {
                    historyRepository.recordContact(contact.lookupKey, contact.id, contact.displayName, number)
                } else {
                    historyRepository.recordNumber(number, dialed = true)
                }
            }
        }
        if (searchActive && s.clearSearchAfterCall) exitSearch()
        // 바로 발신했다면 키패드 입력을 비운다 (다이얼 화면을 거치는 경우는 돌아왔을 때 다시 쓸 수 있게 둔다)
        if (direct && number == _dialDigits.value) setDialDigits("")
    }

    fun hasCallPermission(): Boolean = dialer.hasCallPermission()

    fun notifyCopied() = sendEffect(UiEffect.Message(R.string.message_number_copied))

    fun sendSms(number: String) {
        sendEffect(UiEffect.Launch(listOf(dialer.smsIntent(number)), R.string.error_no_sms_app))
    }

    // ---------------------------------------------------------------- 시스템 연락처 앱 위임

    fun viewInContactsApp(contact: Contact) =
        sendEffect(UiEffect.Launch(listOf(dialer.viewContactIntent(contact.id, contact.lookupKey)), R.string.error_no_contacts_app))

    fun editInContactsApp(contact: Contact) =
        sendEffect(UiEffect.Launch(listOf(dialer.editContactIntent(contact.id, contact.lookupKey)), R.string.error_no_contacts_app))

    fun createContact(number: String? = null) =
        sendEffect(UiEffect.Launch(listOf(dialer.insertContactIntent(number)), R.string.error_no_contacts_app))

    fun addNumberToContacts(number: String) =
        sendEffect(
            UiEffect.Launch(
                listOf(dialer.insertOrEditContactIntent(number), dialer.insertContactIntent(number)),
                R.string.error_no_contacts_app,
            ),
        )

    fun openSystemCallLog() = sendEffect(UiEffect.Launch(dialer.callLogIntents(), R.string.error_no_dialer))

    fun openAppSettings() = sendEffect(UiEffect.Launch(listOf(dialer.appDetailsSettingsIntent()), R.string.error_generic))

    fun openDefaultAppsSettings() =
        sendEffect(UiEffect.Launch(listOf(dialer.defaultAppsSettingsIntent(), dialer.appDetailsSettingsIntent()), R.string.error_generic))

    // ---------------------------------------------------------------- 키패드

    private fun setDialDigits(value: String) {
        _dialDigits.value = value.take(MAX_DIAL_LENGTH)
    }

    fun appendDial(c: Char) {
        if (!KoreanPhoneNumbers.isDialChar(c)) return
        if (c == '+' && _dialDigits.value.isNotEmpty()) return
        setDialDigits(_dialDigits.value + c)
    }

    fun backspaceDial() {
        setDialDigits(_dialDigits.value.dropLast(1))
    }

    fun clearDial() = setDialDigits("")

    fun pasteDial(text: String) {
        val clean = KoreanPhoneNumbers.sanitizeDialInput(text)
        if (clean.isNotEmpty()) setDialDigits(clean) else sendEffect(UiEffect.Message(R.string.message_paste_no_number))
    }

    /** 번호가 비어 있으면 마지막으로 건 번호를 채운다 (한 번 더 누르면 발신). */
    fun callFromKeypad() {
        val digits = _dialDigits.value
        if (digits.isEmpty()) {
            val last = lastDialedNumber.value
            if (last.isNullOrEmpty()) sendEffect(UiEffect.Message(R.string.message_enter_number)) else setDialDigits(last)
            return
        }
        callNumber(digits)
    }

    // ---------------------------------------------------------------- 통화기록

    fun setCallFilter(filter: CallFilter) {
        _callFilter.value = filter
    }

    fun requestCallLogPermission() {
        val permission = callHistoryRepository.requiredPermission ?: return
        sendEffect(UiEffect.RequestPermission(permission, PermissionPurpose.CALL_LOG))
    }

    // ---------------------------------------------------------------- 통화 녹음

    private fun buildRecents(entries: List<CallLogEntry>, filter: CallFilter, cs: ContactsState, recs: List<CallRecording>): RecentsUiState {
        val ready = cs as? ContactsState.Ready
        val byName = ready?.contacts?.associateBy { SearchText.normalize(it.displayName) }.orEmpty()
        val matched = RecordingMatcher.match(entries, recs) { call ->
            ready?.findByNumber(call.nationalDigits)?.displayName ?: call.cachedName
        }
        val filtered = if (filter == CallFilter.RECORDED) entries.filter { it.id in matched } else CallLogGrouping.filter(entries, filter)
        val names = HashMap<String, Contact>()
        if (ready != null) {
            for (e in filtered) ready.findByNumber(e.nationalDigits)?.let { names[e.nationalDigits] = it }
        }
        val items = recs.map { r ->
            val contact = r.labelDigits?.let { ready?.findByNumber(it) } ?: r.label?.let { byName[SearchText.normalize(it)] }
            RecordingItem(r, contact)
        }
        return RecentsUiState(CallLogGrouping.groupByDay(filtered), names, matched, items)
    }

    /** 연락처 한 명의 통화 녹음 (파일 이름 또는 통화기록 연결로 찾음) */
    fun recordingsFor(contact: Contact): List<CallRecording> =
        RecordingMatcher.forContact(contact, recordings.value, recentsUi.value.recordingByCallId, callLogEntries.value)

    fun recordingsForNumber(number: String): List<CallRecording> =
        RecordingMatcher.forNumber(KoreanPhoneNumbers.toNationalDigits(number), recordings.value, recentsUi.value.recordingByCallId, callLogEntries.value)

    fun rescanRecordings() {
        recordingsRepository.rescan()
        sendEffect(UiEffect.Message(R.string.message_recordings_rescan))
    }

    fun playRecording(recording: CallRecording, title: String) {
        playing = PlayingRecording(recording, title)
    }

    fun closePlayer() {
        playing = null
    }

    fun openRecordingExternally(recording: CallRecording) =
        sendEffect(UiEffect.Launch(listOf(dialer.audioIntent(recording.uri)), R.string.error_no_player))

    fun requestRecordingsPermission() =
        sendEffect(UiEffect.RequestPermission(recordingsRepository.requiredPermission, PermissionPurpose.RECORDINGS))

    // ---------------------------------------------------------------- 설정 / 권한

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setDirectCall(enabled: Boolean) {
        if (enabled && !dialer.hasCallPermission()) {
            pendingDirectCallEnable = true
            sendEffect(UiEffect.RequestPermission(Manifest.permission.CALL_PHONE, PermissionPurpose.CALL_PHONE))
            return
        }
        updateSettings { it.copy(directCall = enabled) }
    }

    fun requestContactsPermission() {
        sendEffect(UiEffect.RequestPermission(Manifest.permission.READ_CONTACTS, PermissionPurpose.CONTACTS))
    }

    fun onPermissionResult(purpose: PermissionPurpose, granted: Boolean) {
        when (purpose) {
            PermissionPurpose.CONTACTS -> {
                updateSettings { it.copy(contactsPermissionRequested = true) }
                contactsRepository.reload()
                if (!granted) sendEffect(UiEffect.Message(R.string.message_contacts_denied))
            }
            PermissionPurpose.CALL_PHONE -> {
                if (pendingDirectCallEnable && granted) updateSettings { it.copy(directCall = true) }
                if (!granted) sendEffect(UiEffect.Message(R.string.message_call_phone_denied))
                pendingDirectCallEnable = false
            }
            PermissionPurpose.CALL_LOG -> callHistoryRepository.onPermissionResult(granted)
            PermissionPurpose.RECORDINGS -> {
                recordingsRepository.onPermissionResult(granted)
                if (!granted) sendEffect(UiEffect.Message(R.string.message_recordings_denied))
            }
        }
    }

    private fun sendEffect(effect: UiEffect) {
        _effects.trySend(effect)
    }

    companion object {
        private const val KEY_DEST = "destination"
        private const val KEY_QUERY = "query"
        private const val KEY_DIAL = "dial"
        private const val KEY_SEARCH_ACTIVE = "searchActive"
        private const val MAX_RECENT_IN_SEARCH = 30
        private const val MAX_FREQUENT = 5
        private const val MAX_DIAL_LENGTH = 40

        fun StartTab.toDestination(): Destination = when (this) {
            StartTab.HOME -> Destination.HOME
            StartTab.CONTACTS -> Destination.CONTACTS
            StartTab.FAVORITES -> Destination.FAVORITES
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BaroDialApplication
                val c = app.container
                MainViewModel(
                    contactsRepository = c.contactsRepository,
                    favoritesRepository = c.favoritesRepository,
                    historyRepository = c.searchHistoryRepository,
                    settingsRepository = c.settingsRepository,
                    callHistoryRepository = c.callHistoryRepository,
                    recordingsRepository = c.callRecordingsRepository,
                    dialer = c.dialerLauncher,
                    engine = c.searchEngine,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
