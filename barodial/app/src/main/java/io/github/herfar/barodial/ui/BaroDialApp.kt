package io.github.herfar.barodial.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.herfar.barodial.R
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallLogGrouping
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.dial.DialerLauncher
import io.github.herfar.barodial.ui.components.AppTopBar
import io.github.herfar.barodial.ui.components.AddContactSheet
import io.github.herfar.barodial.ui.components.NumberPickerSheet
import io.github.herfar.barodial.ui.components.RecordingPlayerSheet
import io.github.herfar.barodial.ui.components.SearchDock
import io.github.herfar.barodial.ui.contacts.ContactDetailScreen
import io.github.herfar.barodial.ui.contacts.ContactsScreen
import io.github.herfar.barodial.ui.contacts.DetailActions
import io.github.herfar.barodial.ui.contacts.NumberDetailActions
import io.github.herfar.barodial.ui.contacts.NumberDetailScreen
import io.github.herfar.barodial.ui.dialer.DialerActions
import io.github.herfar.barodial.ui.dialer.DialerPane
import io.github.herfar.barodial.ui.favorites.FavoritesActions
import io.github.herfar.barodial.ui.favorites.FavoritesScreen
import io.github.herfar.barodial.ui.home.HomeActions
import io.github.herfar.barodial.ui.home.HomeDialer
import io.github.herfar.barodial.ui.home.HomeScreen
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.recents.RecentsActions
import io.github.herfar.barodial.ui.recents.RecentsScreen
import io.github.herfar.barodial.ui.search.OnlineActions
import io.github.herfar.barodial.ui.search.SearchActions
import io.github.herfar.barodial.ui.search.SearchResults
import io.github.herfar.barodial.ui.settings.DiagnosticsActions
import io.github.herfar.barodial.ui.settings.OnlineSearchSetupScreen
import io.github.herfar.barodial.ui.settings.OnlineSetupActions
import io.github.herfar.barodial.ui.settings.RecordingDiagnosticsScreen
import io.github.herfar.barodial.ui.settings.SettingsActions
import io.github.herfar.barodial.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/** 이 너비 이상이면 펼친 화면 레이아웃(목록 + 오른쪽 창)을 쓴다. */
private val ExpandedWidth = 600.dp

/**
 * 앱 최상위 화면.
 *
 * - 접힌 화면(좁은 창): 탭 화면(좌우 스와이프) + 하단 검색창 + 하단 탭. 홈 탭이 다이얼이다.
 *   상세/설정은 전체 화면으로 덮는다.
 * - 펼친 화면(넓은 창): 왼쪽은 같은 탭 화면(검색창 위치 동일), 오른쪽은 키패드 또는 연락처 상세.
 * - 두 레이아웃은 같은 MainPane 을 movableContentOf 로 옮겨 쓰므로, 접고 펼쳐도
 *   검색어·스크롤 위치·포커스가 그대로 유지된다.
 */
@Composable
fun BaroDialApp(viewModel: MainViewModel, dialerLauncher: DialerLauncher) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = remember(context) { context.findActivity() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var pendingPermission by rememberSaveable { mutableStateOf<PermissionPurpose?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.let { viewModel.onPermissionResult(it, granted) }
        pendingPermission = null
    }

    // 시스템 연락처 앱의 등록·편집 화면. 같은 작업(task)에서 열리므로 저장·취소 후 이 앱으로 돌아온다.
    val contactEditorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onContactEditorClosed(saved = result.resultCode == Activity.RESULT_OK, resultUri = result.data?.data)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    fun showMessage(text: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.Launch -> {
                    if (!dialerLauncher.launchFirst(context, effect.intents)) showMessage(resources.getString(effect.failureMessage))
                }
                is UiEffect.EditContact -> {
                    val launched = effect.intents.any { intent ->
                        try {
                            contactEditorLauncher.launch(intent)
                            true
                        } catch (e: ActivityNotFoundException) {
                            false
                        }
                    }
                    if (!launched) {
                        viewModel.onContactEditorLaunchFailed()
                        showMessage(resources.getString(effect.failureMessage))
                    }
                }
                is UiEffect.Message -> showMessage(
                    if (effect.arg != null) resources.getString(effect.message, effect.arg) else resources.getString(effect.message),
                )
                is UiEffect.RequestPermission -> {
                    val permanentlyDenied = effect.purpose == PermissionPurpose.CONTACTS &&
                        settings.contactsPermissionRequested &&
                        activity != null &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, effect.permission)
                    if (permanentlyDenied) {
                        viewModel.openAppSettings()
                        showMessage(resources.getString(R.string.message_contacts_open_settings))
                    } else {
                        pendingPermission = effect.purpose
                        permissionLauncher.launch(effect.permission)
                    }
                }
                UiEffect.HideKeyboard -> {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            }
        }
    }

    BackHandler(enabled = viewModel.canHandleBack) { viewModel.onBack() }

    // Surface 로 감싸 모든 글자가 테마의 onBackground 색을 기본으로 쓰게 한다 (다크 모드 대응).
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= ExpandedWidth
            val mainPane = remember { movableContentOf { isExpanded: Boolean -> MainPane(viewModel, isExpanded) } }
            if (expanded) {
                Row(Modifier.fillMaxSize().testTag("expandedLayout")) {
                    Box(Modifier.weight(0.45f).fillMaxHeight()) { mainPane(true) }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(0.55f).fillMaxHeight()) { RightPane(viewModel) }
                }
            } else {
                val covered = viewModel.backStack.isNotEmpty()
                Box(Modifier.fillMaxSize().then(if (covered) Modifier.clearAndSetSemantics { } else Modifier)) {
                    mainPane(false)
                }
                CompactOverlay(viewModel)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(bottom = 150.dp),
        )
    }
    }

    viewModel.numberPicker?.let { picker ->
        NumberPickerSheet(
            contact = picker.contact,
            onPick = { phone, remember -> viewModel.onNumberPicked(picker.contact, phone, remember) },
            onDismiss = viewModel::dismissNumberPicker,
        )
    }

    viewModel.addContactNumber?.let { number ->
        AddContactSheet(
            number = number,
            onCreateNew = { viewModel.createContactWithNumber(number) },
            onAddToExisting = { viewModel.addNumberToExistingContact(number) },
            onDismiss = viewModel::dismissAddContact,
        )
    }

    viewModel.playing?.let { playing ->
        RecordingPlayerSheet(
            recording = playing.recording,
            title = playing.title,
            onDismiss = viewModel::closePlayer,
            onOpenExternally = { viewModel.openRecordingExternally(playing.recording) },
        )
    }
}

@Composable
private fun rememberDialerActions(vm: MainViewModel): DialerActions = remember(vm) {
    DialerActions(
        digit = vm::appendDial,
        backspace = vm::backspaceDial,
        clear = vm::clearDial,
        call = vm::callFromKeypad,
        addContact = vm::addNumberToContacts,
        sms = vm::sendSms,
        openContact = vm::openContact,
        callContact = vm::callContact,
        paste = vm::pasteDial,
        numberCopied = vm::notifyCopied,
    )
}

@Composable
private fun MainPane(vm: MainViewModel, expanded: Boolean) {
    val search by vm.searchUi.collectAsStateWithLifecycle()
    val home by vm.homeUi.collectAsStateWithLifecycle()
    val favorites by vm.favoritesUi.collectAsStateWithLifecycle()
    val contacts by vm.contactsState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val recents by vm.recentsUi.collectAsStateWithLifecycle()
    val callAvailability by vm.callHistoryAvailability.collectAsStateWithLifecycle()
    val recordingsAvailability by vm.recordingsAvailability.collectAsStateWithLifecycle()
    val callFilter by vm.callFilter.collectAsStateWithLifecycle()
    val preferred by vm.preferredNumbers.collectAsStateWithLifecycle()
    val dialer by vm.dialerUi.collectAsStateWithLifecycle()
    val contactsListState = rememberLazyListState()
    val selectedId = if (expanded) (vm.backStack.lastOrNull() as? Route.Detail)?.contactId else null

    // 탭 ↔ 좌우 스와이프 동기화
    val pagerState = rememberPagerState(initialPage = vm.destination.ordinal) { Destination.entries.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { vm.onPageSettled(Destination.entries[it]) }
    }
    LaunchedEffect(vm.destination) {
        val target = vm.destination.ordinal
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    val shown = Destination.entries[pagerState.currentPage]

    val online by vm.onlineUi.collectAsStateWithLifecycle()
    val nearbySelected by vm.onlineNearbySelected.collectAsStateWithLifecycle()
    val onlineActions = remember(vm) {
        OnlineActions(
            callPlace = vm::callPlace,
            openPlace = vm::openPlace,
            retry = vm::retryOnlineSearch,
            loadMore = vm::loadMoreOnline,
            setNearby = vm::setOnlineNearby,
            requestLocation = vm::requestLocationPermission,
            openSettings = vm::openOnlineSearchSetup,
            dismissHint = vm::dismissOnlineHint,
            enterContactManually = vm::createContact,
        )
    }
    val searchActions = remember(vm) {
        SearchActions(
            openContact = vm::openContact,
            callContact = vm::callContact,
            callNumber = vm::callNumber,
            sendSms = vm::sendSms,
            addToContacts = vm::addNumberToContacts,
            removeHistory = vm::removeHistory,
            clearHistory = vm::clearHistory,
            requestContactsPermission = vm::requestContactsPermission,
        )
    }
    val homeActions = remember(vm) {
        HomeActions(
            requestContactsPermission = vm::requestContactsPermission,
            callContact = { vm.callContact(it) },
            openContact = vm::openContact,
            showFavorites = { vm.selectDestination(Destination.FAVORITES) },
        )
    }
    val dialerActions = rememberDialerActions(vm)
    val favoritesActions = remember(vm) {
        FavoritesActions(
            call = { vm.callContact(it) },
            open = vm::openContact,
            toggleFavorite = vm::toggleFavorite,
            move = vm::moveFavorite,
            requestContactsPermission = vm::requestContactsPermission,
        )
    }
    val recentsActions = remember(vm) {
        RecentsActions(
            openSystemCallLog = vm::openSystemCallLog,
            requestPermission = vm::requestCallLogPermission,
            openAppSettings = vm::openAppSettings,
            setFilter = vm::setCallFilter,
            callNumber = vm::callNumber,
            sendSms = vm::sendSms,
            openContact = vm::openContact,
            openNumber = vm::openNumber,
            playRecording = vm::playRecording,
            requestRecordingsPermission = vm::requestRecordingsPermission,
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("mainPane")) {
        MainTopBar(vm, shown, contacts, search)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // 탭 화면은 검색 중에도 그대로 두고 그 위에 검색 결과를 덮는다 (검색을 닫으면 보던 화면 그대로).
            HorizontalPager(
                state = pagerState,
                key = { Destination.entries[it].name },
                beyondViewportPageCount = 1,
                userScrollEnabled = !vm.searchActive,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tabPager")
                    .then(if (vm.searchActive) Modifier.clearAndSetSemantics { } else Modifier),
            ) { page ->
                when (Destination.entries[page]) {
                    Destination.HOME -> if (expanded) {
                        HomeScreen(home, contacts, homeActions)
                    } else {
                        HomeDialer(home, contacts, dialer, settings.keypadHaptics, homeActions, dialerActions)
                    }
                    Destination.CONTACTS -> ContactsScreen(
                        state = contacts,
                        listState = contactsListState,
                        selectedContactId = selectedId,
                        onOpen = vm::openContact,
                        onCall = { vm.callContact(it) },
                        onRequestPermission = vm::requestContactsPermission,
                        onCreateContact = { vm.createContact() },
                    )
                    Destination.RECENTS -> RecentsScreen(
                        availability = callAvailability,
                        recordingsAvailability = recordingsAvailability,
                        state = recents,
                        filter = callFilter,
                        dialerLabel = vm.dialerInfo?.label,
                        canOpenCallLogScreen = vm.canOpenCallLogScreen,
                        actions = recentsActions,
                    )
                    Destination.FAVORITES -> FavoritesScreen(
                        state = favorites,
                        contactsState = contacts,
                        editMode = vm.favoritesEditMode,
                        preferredDigits = { preferred[it.lookupKey] },
                        actions = favoritesActions,
                    )
                }
            }
            if (vm.searchActive) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SearchResults(
                        state = search,
                        nearSearchBar = settings.resultsNearSearchBar,
                        actions = searchActions,
                        online = online,
                        nearbySelected = nearbySelected,
                        onlineActions = onlineActions,
                    )
                }
            }
        }
        SearchDock(
            state = vm.searchField,
            active = vm.searchActive,
            focusRequest = vm.searchFocusRequest,
            showKeypadButton = false,
            onFocused = vm::onSearchFocused,
            onExit = vm::exitSearch,
            onKeypad = { vm.openKeypad() },
        )
        if (vm.searchActive || WindowInsets.isImeVisible) {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars.union(WindowInsets.ime)))
        } else {
            BottomNav(current = shown, onSelect = vm::selectDestination)
        }
    }
}

@Composable
private fun MainTopBar(vm: MainViewModel, shown: Destination, contacts: ContactsState, search: SearchUiState) {
    val settingsButton: @Composable () -> Unit = {
        IconButton(onClick = vm::openSettings, modifier = Modifier.testTag("settingsButton")) {
            Icon(AppIcons.Settings, contentDescription = stringResource(R.string.settings_title))
        }
    }
    if (vm.searchActive) {
        AppTopBar(
            title = stringResource(R.string.search_title),
            subtitle = when {
                search.query.isEmpty() -> stringResource(R.string.search_recent_title)
                else -> stringResource(R.string.search_result_count, search.contactResultCount)
            },
        ) {
            TextButton(onClick = vm::exitSearch) { Text(stringResource(R.string.action_close)) }
        }
        return
    }
    when (shown) {
        Destination.HOME -> AppTopBar(title = stringResource(R.string.app_name)) { settingsButton() }
        Destination.CONTACTS -> AppTopBar(
            title = stringResource(R.string.tab_contacts),
            subtitle = (contacts as? ContactsState.Ready)?.let { stringResource(R.string.contacts_count, it.contacts.size) },
        ) {
            IconButton(onClick = { vm.createContact() }) {
                Icon(AppIcons.PersonAdd, contentDescription = stringResource(R.string.action_new_contact))
            }
            settingsButton()
        }
        Destination.RECENTS -> AppTopBar(title = stringResource(R.string.tab_recents)) {
            IconButton(onClick = vm::openSystemCallLog) {
                Icon(AppIcons.OpenInNew, contentDescription = stringResource(R.string.recents_open_system, vm.dialerInfo?.label ?: stringResource(R.string.default_phone_app)))
            }
            settingsButton()
        }
        Destination.FAVORITES -> AppTopBar(title = stringResource(R.string.tab_favorites)) {
            TextButton(onClick = vm::toggleFavoritesEditMode, modifier = Modifier.testTag("favoritesEdit")) {
                Text(stringResource(if (vm.favoritesEditMode) R.string.action_done else R.string.action_edit))
            }
            settingsButton()
        }
    }
}

@Composable
private fun BottomNav(current: Destination, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        for (d in Destination.entries) {
            val (icon, label) = when (d) {
                Destination.HOME -> AppIcons.Home to R.string.tab_home
                Destination.CONTACTS -> AppIcons.People to R.string.tab_contacts
                Destination.RECENTS -> AppIcons.History to R.string.tab_recents
                Destination.FAVORITES -> AppIcons.Star to R.string.tab_favorites
            }
            NavigationBarItem(
                selected = d == current,
                onClick = { onSelect(d) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label), maxLines = 1) },
                modifier = Modifier.testTag("tab_${d.name}"),
            )
        }
    }
}

@Composable
private fun CompactOverlay(vm: MainViewModel) {
    AnimatedContent(
        targetState = vm.backStack.lastOrNull(),
        transitionSpec = {
            (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 5 }) togetherWith fadeOut(tween(140))
        },
        label = "route",
        modifier = Modifier.fillMaxSize(),
    ) { route ->
        when (route) {
            null -> Box(Modifier.fillMaxSize())
            is Route.Detail -> DetailRoute(vm, route, AppIcons.ArrowBack)
            is Route.NumberDetail -> NumberDetailRoute(vm, route, AppIcons.ArrowBack)
            Route.Settings -> SettingsRoute(vm, AppIcons.ArrowBack)
            Route.RecordingDiagnostics -> DiagnosticsRoute(vm, AppIcons.ArrowBack)
            Route.OnlineSearchSetup -> OnlineSetupRoute(vm, AppIcons.ArrowBack)
        }
    }
}

@Composable
private fun RightPane(vm: MainViewModel) {
    AnimatedContent(
        targetState = vm.backStack.lastOrNull(),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "pane",
        modifier = Modifier.fillMaxSize().testTag("rightPane"),
    ) { route ->
        when (route) {
            null -> KeypadPane(vm)
            is Route.Detail -> DetailRoute(vm, route, AppIcons.Close)
            is Route.NumberDetail -> NumberDetailRoute(vm, route, AppIcons.Close)
            Route.Settings -> SettingsRoute(vm, AppIcons.Close)
            Route.RecordingDiagnostics -> DiagnosticsRoute(vm, AppIcons.ArrowBack)
            Route.OnlineSearchSetup -> OnlineSetupRoute(vm, AppIcons.ArrowBack)
        }
    }
}

@Composable
private fun DetailRoute(vm: MainViewModel, route: Route.Detail, navigationIcon: ImageVector) {
    val cs by vm.contactsState.collectAsStateWithLifecycle()
    val favorites by vm.favoriteEntries.collectAsStateWithLifecycle()
    val preferred by vm.preferredNumbers.collectAsStateWithLifecycle()
    val callLog by vm.callLogEntries.collectAsStateWithLifecycle()
    val availability by vm.callHistoryAvailability.collectAsStateWithLifecycle()
    val allRecordings by vm.recordings.collectAsStateWithLifecycle()
    val recents by vm.recentsUi.collectAsStateWithLifecycle()
    val ready = cs as? ContactsState.Ready
    if (ready == null && cs == ContactsState.Loading) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val contact = ready?.find(route.lookupKey, route.contactId)
    val isFavorite = contact != null && favorites.any { it.lookupKey == contact.lookupKey || it.contactId == contact.id }
    val calls = if (availability == CallHistoryAvailability.Available && contact != null) {
        remember(callLog, contact) { CallLogGrouping.forNumbers(callLog, contact.phones.mapTo(HashSet()) { it.nationalDigits }) }
    } else {
        null
    }
    val recordings = if (contact != null) {
        remember(contact, allRecordings, recents.recordingByCallId) { vm.recordingsFor(contact) }
    } else {
        emptyList()
    }
    val actions = remember(vm) {
        DetailActions(
            navigate = vm::popRoute,
            call = vm::callContact,
            sms = vm::sendSms,
            toggleFavorite = vm::toggleFavorite,
            edit = vm::editInContactsApp,
            viewInContacts = vm::viewInContactsApp,
            setPreferred = vm::setPreferredNumber,
            numberCopied = vm::notifyCopied,
            playRecording = vm::playRecording,
        )
    }
    ContactDetailScreen(
        contact = contact,
        isFavorite = isFavorite,
        preferredDigits = contact?.let { preferred[it.lookupKey] },
        calls = calls,
        navigationIcon = navigationIcon,
        actions = actions,
        recordings = recordings,
        recordingByCallId = recents.recordingByCallId,
    )
}

@Composable
private fun NumberDetailRoute(vm: MainViewModel, route: Route.NumberDetail, navigationIcon: ImageVector) {
    val callLog by vm.callLogEntries.collectAsStateWithLifecycle()
    val availability by vm.callHistoryAvailability.collectAsStateWithLifecycle()
    val allRecordings by vm.recordings.collectAsStateWithLifecycle()
    val recents by vm.recentsUi.collectAsStateWithLifecycle()
    val digits = KoreanPhoneNumbers.toNationalDigits(route.number)
    val calls = if (availability == CallHistoryAvailability.Available) {
        remember(callLog, digits) { CallLogGrouping.forNumbers(callLog, setOf(digits)) }
    } else {
        null
    }
    val recordings = remember(route.number, allRecordings, recents.recordingByCallId) { vm.recordingsForNumber(route.number) }
    val actions = remember(vm) {
        NumberDetailActions(
            navigate = vm::popRoute,
            call = vm::callNumber,
            sms = vm::sendSms,
            addContact = vm::addNumberToContacts,
            playRecording = vm::playRecording,
        )
    }
    NumberDetailScreen(
        number = route.number,
        calls = calls,
        recordings = recordings,
        recordingByCallId = recents.recordingByCallId,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
private fun DiagnosticsRoute(vm: MainViewModel, navigationIcon: ImageVector) {
    val recordingsAvailability by vm.recordingsAvailability.collectAsStateWithLifecycle()
    val callAvailability by vm.callHistoryAvailability.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val callLog by vm.callLogEntries.collectAsStateWithLifecycle()
    val recents by vm.recentsUi.collectAsStateWithLifecycle()
    val cs by vm.contactsState.collectAsStateWithLifecycle()
    val actions = remember(vm) {
        DiagnosticsActions(
            back = vm::popRoute,
            rescan = vm::rescanRecordings,
            requestPermission = vm::requestRecordingsPermission,
            play = vm::playRecording,
        )
    }
    RecordingDiagnosticsScreen(
        recordingsAvailability = recordingsAvailability,
        callAvailability = callAvailability,
        recordings = recordings,
        calls = callLog,
        recordingByCallId = recents.recordingByCallId,
        nameFor = { call -> (cs as? ContactsState.Ready)?.findByNumber(call.nationalDigits)?.displayName ?: call.cachedName },
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/** 펼친 화면 오른쪽 창의 기본 화면: 키패드 */
@Composable
private fun KeypadPane(vm: MainViewModel) {
    val state by vm.dialerUi.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val actions = rememberDialerActions(vm)
    Surface(Modifier.fillMaxSize().testTag("keypadPane"), color = MaterialTheme.colorScheme.background) {
        DialerPane(
            state = state,
            haptics = settings.keypadHaptics,
            actions = actions,
            topBar = { AppTopBar(title = stringResource(R.string.keypad)) },
        )
    }
}

@Composable
private fun SettingsRoute(vm: MainViewModel, navigationIcon: ImageVector) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cs by vm.contactsState.collectAsStateWithLifecycle()
    val availability by vm.callHistoryAvailability.collectAsStateWithLifecycle()
    val recordingsAvailability by vm.recordingsAvailability.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val actions = remember(vm) {
        SettingsActions(
            back = vm::popRoute,
            update = vm::updateSettings,
            setDirectCall = vm::setDirectCall,
            clearHistory = vm::clearHistory,
            requestContacts = vm::requestContactsPermission,
            openAppSettings = vm::openAppSettings,
            openDefaultApps = vm::openDefaultAppsSettings,
            requestRecordings = vm::requestRecordingsPermission,
            openDiagnostics = vm::openRecordingDiagnostics,
            openOnlineSetup = vm::openOnlineSearchSetup,
        )
    }
    SettingsScreen(
        settings = settings,
        contactsState = cs,
        callPermissionGranted = vm.hasCallPermission(),
        callHistory = availability,
        recordings = recordingsAvailability,
        recordingCount = recordings.size,
        dialerInfo = vm.dialerInfo,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
private fun OnlineSetupRoute(vm: MainViewModel, navigationIcon: ImageVector) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val actions = remember(vm) {
        OnlineSetupActions(
            back = {
                vm.clearConnectionTest()
                vm.popRoute()
            },
            setEnabled = vm::setOnlineSearchEnabled,
            test = vm::testOnlineConnection,
            save = vm::saveOnlineSearchSetup,
        )
    }
    OnlineSearchSetupScreen(settings = settings, testState = vm.connectionTest, navigationIcon = navigationIcon, actions = actions)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
