package io.github.herfar.barodial.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.calllog.CallFilter
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.testing.FakeCallHistoryRepository
import io.github.herfar.barodial.testing.FakeCallRecordingsRepository
import io.github.herfar.barodial.data.history.HistoryKind
import io.github.herfar.barodial.data.settings.AppSettings
import io.github.herfar.barodial.testing.FakeContactsRepository
import io.github.herfar.barodial.testing.Harness
import io.github.herfar.barodial.testing.InMemorySettingsRepository
import io.github.herfar.barodial.testing.TestContacts
import io.github.herfar.barodial.testing.awaitUntil
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var h: Harness
    private val effects = mutableListOf<UiEffect>()

    private fun start(harness: Harness = Harness(app)) {
        h = harness
        h.viewModel.viewModelScope.launch { h.viewModel.effects.collect { effects += it } }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Before
    fun setUp() = effects.clear()

    @After
    fun tearDown() {
        shadowOf(app).denyPermissions(Manifest.permission.CALL_PHONE)
    }

    private fun search(text: String) {
        h.viewModel.onSearchFocused()
        // 변경을 즉시 적용해 snapshotFlow 관찰자에게 동기적으로 알린다.
        Snapshot.withMutableSnapshot { h.viewModel.searchField.setTextAndPlaceCursorAtEnd(text) }
        awaitUntil(
            describe = { "query='${h.viewModel.searchField.text}' ui='${h.viewModel.searchUi.value.query}' contacts=${h.viewModel.contactsState.value::class.simpleName}" },
        ) { h.viewModel.searchUi.value.query == text.trim() }
    }

    private fun launchedIntents(): List<Intent> = effects.filterIsInstance<UiEffect.Launch>().map { it.intents.first() }

    @Test
    fun choseongSearchFindsContact() {
        start()
        search("ㄱㅊㅅ")
        val rows = h.viewModel.searchUi.value.rows
        val hit = rows.filterIsInstance<SearchRow.ContactHit>().single()
        assertEquals("김철수", hit.result.contact.displayName)
    }

    @Test
    fun numberQueryOffersDialRowFirst() {
        start()
        search("010-1234-5678")
        val first = h.viewModel.searchUi.value.rows.first()
        assertTrue(first is SearchRow.DialNumber)
        assertEquals("김철수", (first as SearchRow.DialNumber).contact?.displayName)
    }

    @Test
    fun callingContactUsesActionDialAndRecordsHistory() {
        start()
        search("김철수")
        h.viewModel.callContact(TestContacts.KIM_CHEOLSU)
        awaitUntil { launchedIntents().isNotEmpty() }
        val intent = launchedIntents().single()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:010-1234-5678", intent.data.toString())
        // 발신 후 검색 닫기 (기본 설정)
        assertFalse(h.viewModel.searchActive)
        awaitUntil { h.history.list.value.isNotEmpty() }
        val entry = h.history.list.value.single()
        assertEquals(HistoryKind.CONTACT, entry.kind)
        assertEquals(1, entry.dialCount)
        assertEquals("010-1234-5678", h.history.lastDialed.value)
    }

    @Test
    fun multiNumberContactAsksOnceThenRemembers() {
        start()
        h.viewModel.callContact(TestContacts.MULTI)
        assertNotNull(h.viewModel.numberPicker)
        assertTrue(launchedIntents().isEmpty())

        val work = TestContacts.MULTI.phones[1]
        h.viewModel.onNumberPicked(TestContacts.MULTI, work, remember = true)
        awaitUntil { launchedIntents().size == 1 }
        assertNull(h.viewModel.numberPicker)
        assertEquals("tel:02-333-4444", launchedIntents().last().data.toString())

        awaitUntil { h.viewModel.preferredNumbers.value[TestContacts.MULTI.lookupKey] == "023334444" }
        h.viewModel.callContact(TestContacts.MULTI)
        awaitUntil { launchedIntents().size == 2 }
        assertNull(h.viewModel.numberPicker)
        assertEquals("tel:02-333-4444", launchedIntents().last().data.toString())
    }

    @Test
    fun directCallRequiresPermissionAndNeverAppliesToEmergencyNumbers() {
        start(Harness(app, settings = InMemorySettingsRepository(AppSettings(directCall = false))))
        h.viewModel.setDirectCall(true)
        awaitUntil { effects.any { it is UiEffect.RequestPermission } }
        assertEquals(Manifest.permission.CALL_PHONE, (effects.first { it is UiEffect.RequestPermission } as UiEffect.RequestPermission).permission)

        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        h.viewModel.onPermissionResult(PermissionPurpose.CALL_PHONE, granted = true)
        awaitUntil { h.viewModel.settings.value.directCall }

        h.viewModel.callNumber("01012345678")
        h.viewModel.callNumber("112")
        awaitUntil { launchedIntents().size == 2 }
        assertEquals(Intent.ACTION_CALL, launchedIntents()[0].action)
        assertEquals(Intent.ACTION_DIAL, launchedIntents()[1].action)
    }

    @Test
    fun directCallSettingFallsBackToDialWithoutPermission() {
        start(Harness(app, settings = InMemorySettingsRepository(AppSettings(directCall = true))))
        awaitUntil { h.viewModel.settings.value.directCall }
        h.viewModel.callNumber("01012345678")
        awaitUntil { launchedIntents().isNotEmpty() }
        assertEquals(Intent.ACTION_DIAL, launchedIntents().single().action)
    }

    @Test
    fun keypadEditingAndRedial() {
        start()
        "01012345678".forEach { h.viewModel.appendDial(it) }
        awaitUntil { h.viewModel.dialerUi.value.formatted == "010-1234-5678" }
        assertEquals("김철수", h.viewModel.dialerUi.value.matches.first().contact.displayName)
        h.viewModel.backspaceDial()
        assertEquals("0101234567", h.viewModel.dialDigits.value)
        h.viewModel.appendDial('8')
        h.viewModel.callFromKeypad()
        awaitUntil { launchedIntents().size == 1 }

        h.viewModel.clearDial()
        assertEquals("", h.viewModel.dialDigits.value)
        // 빈 상태에서 통화 버튼: 마지막 번호를 채운다
        awaitUntil { h.history.lastDialed.value != null }
        h.viewModel.callFromKeypad()
        assertEquals("01012345678", h.viewModel.dialDigits.value)
        assertEquals(1, launchedIntents().size)
    }

    @Test
    fun plusOnlyAllowedAsFirstCharacter() {
        start()
        h.viewModel.appendDial('+')
        h.viewModel.appendDial('8')
        h.viewModel.appendDial('+')
        assertEquals("+8", h.viewModel.dialDigits.value)
    }

    @Test
    fun backNavigationOrder() {
        start()
        search("김")
        h.viewModel.openContact(TestContacts.KIM_CHEOLSU)
        assertTrue(h.viewModel.backStack.last() is Route.Detail)
        assertTrue(h.viewModel.onBack()) // 상세 닫기 → 검색 결과로
        assertTrue(h.viewModel.searchActive)
        assertEquals("김", h.viewModel.searchField.text.toString())
        assertTrue(h.viewModel.onBack()) // 검색 닫기
        assertFalse(h.viewModel.searchActive)
        h.viewModel.selectDestination(Destination.CONTACTS)
        assertTrue(h.viewModel.onBack()) // 다른 탭 → 시작 탭
        assertEquals(Destination.HOME, h.viewModel.destination)
        assertFalse(h.viewModel.onBack()) // 앱 종료
    }

    @Test
    fun openingContactFromSearchRecordsRecentSearch() {
        start()
        search("영희")
        h.viewModel.openContact(TestContacts.PARK_YOUNGHEE)
        awaitUntil { h.history.list.value.isNotEmpty() }
        assertEquals(0, h.history.list.value.single().dialCount)
        h.viewModel.onBack()
        h.viewModel.exitSearch()
        h.viewModel.onSearchFocused()
        awaitUntil(describe = { "field='${h.viewModel.searchField.text}' ui=${h.viewModel.searchUi.value} history=${h.history.list.value}" }) {
            h.viewModel.searchUi.value.rows.any { it is SearchRow.RecentContact }
        }
        assertTrue(h.viewModel.searchUi.value.rows.last() is SearchRow.ClearHistory)
    }

    @Test
    fun historyIsNotSavedWhenDisabled() {
        start(Harness(app, settings = InMemorySettingsRepository(AppSettings(saveSearchHistory = false))))
        awaitUntil { !h.viewModel.settings.value.saveSearchHistory }
        h.viewModel.callContact(TestContacts.KIM_CHEOLSU)
        awaitUntil { launchedIntents().isNotEmpty() }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(h.history.list.value.isEmpty())
    }

    @Test
    fun favoritesCombineAppAndSystemStarred() {
        start()
        h.viewModel.toggleFavorite(TestContacts.WANG)
        awaitUntil { h.viewModel.homeUi.value.favorites.size == 2 }
        val favs = h.viewModel.homeUi.value.favorites
        assertEquals("왕서방", favs[0].contact.displayName)
        assertFalse(favs[0].fromSystem)
        assertEquals("박영희", favs[1].contact.displayName) // 연락처 앱 즐겨찾기(STARRED)
        assertTrue(favs[1].fromSystem)

        h.viewModel.toggleFavorite(TestContacts.WANG)
        awaitUntil { h.viewModel.homeUi.value.favorites.size == 1 }
    }

    @Test
    fun withoutContactsPermissionNumberSearchAndKeypadStillWork() {
        start(Harness(app, contacts = FakeContactsRepository(ContactsState.PermissionRequired)))
        search("0212345678")
        val rows = h.viewModel.searchUi.value.rows
        assertTrue(rows.first() is SearchRow.DialNumber)
        assertTrue(h.viewModel.searchUi.value.contactsPermissionMissing)

        search("김철수")
        assertTrue(h.viewModel.searchUi.value.rows.single() is SearchRow.ContactsPermissionHint)

        h.viewModel.appendDial('1')
        h.viewModel.appendDial('1')
        h.viewModel.appendDial('9')
        h.viewModel.callFromKeypad()
        awaitUntil { launchedIntents().isNotEmpty() }
        assertEquals("tel:119", launchedIntents().single().data.toString())
    }

    @Test
    fun contactsPermissionResultTriggersReload() {
        start(Harness(app, contacts = FakeContactsRepository(ContactsState.PermissionRequired)))
        h.viewModel.onPermissionResult(PermissionPurpose.CONTACTS, granted = true)
        awaitUntil { h.viewModel.settings.value.contactsPermissionRequested }
        assertEquals(1, h.contacts.reloadCount)
    }

    @Test
    fun openKeypadGoesToHomeDialer() {
        start()
        h.viewModel.selectDestination(Destination.CONTACTS)
        h.viewModel.openContact(TestContacts.KIM_CHEOLSU)
        h.viewModel.openKeypad("010-1111-2222")
        assertEquals(Destination.HOME, h.viewModel.destination)
        assertTrue(h.viewModel.backStack.isEmpty())
        assertEquals("01011112222", h.viewModel.dialDigits.value)
    }

    @Test
    fun recentsFilterResetsToAllWhenLeavingTab() {
        start()
        h.viewModel.onPageSettled(Destination.RECENTS)
        h.viewModel.setCallFilter(CallFilter.RECORDED)
        h.viewModel.onPageSettled(Destination.FAVORITES)
        assertEquals(CallFilter.ALL, h.viewModel.callFilter.value)
        h.viewModel.selectDestination(Destination.RECENTS)
        h.viewModel.setCallFilter(CallFilter.MISSED)
        h.viewModel.selectDestination(Destination.HOME)
        assertEquals(CallFilter.ALL, h.viewModel.callFilter.value)
    }

    @Test
    fun openNumberShowsNumberDetailOrContact() {
        start()
        h.viewModel.openNumber("02-999-8888")
        assertEquals(Route.NumberDetail("02-999-8888"), h.viewModel.backStack.last())
        // 연락처에 있는 번호면 연락처 상세로 (번호 상세를 대신한다)
        h.viewModel.openNumber("010-1234-5678")
        assertEquals(Route.Detail(TestContacts.KIM_CHEOLSU.lookupKey, TestContacts.KIM_CHEOLSU.id), h.viewModel.backStack.last())
        assertEquals(1, h.viewModel.backStack.size)
    }

    @Test
    fun swipeSettlingChangesTabAndBackReturnsHome() {
        start()
        h.viewModel.onPageSettled(Destination.RECENTS)
        assertEquals(Destination.RECENTS, h.viewModel.destination)
        assertTrue(h.viewModel.onBack())
        assertEquals(Destination.HOME, h.viewModel.destination)
    }

    @Test
    fun recordingsAreMatchedToCallsAndContacts() {
        val t = 1_763_800_000_000L
        val calls = listOf(
            CallLogEntry(1, "010-1234-5678", "01012345678", "김철수", CallType.INCOMING, t, 300),
            CallLogEntry(2, "02-555-1234", "025551234", null, CallType.OUTGOING, t - 86_400_000, 60),
            CallLogEntry(3, "010-9876-5432", "01098765432", null, CallType.MISSED, t - 3_600_000, 0),
        )
        val recs = listOf(
            CallRecording(10, "content://r/10", "통화 녹음 김철수_x.m4a", "김철수", null, t + 15_000, 290_000, 1),
            CallRecording(11, "content://r/11", "통화 녹음 025551234_x.m4a", "025551234", "025551234", t - 86_400_000 + 5_000, 55_000, 1),
        )
        start(
            Harness(
                app,
                callHistory = FakeCallHistoryRepository(CallHistoryAvailability.Available, calls),
                recordings = FakeCallRecordingsRepository(RecordingsAvailability.Available, recs),
            ),
        )
        awaitUntil { h.viewModel.recentsUi.value.recordingByCallId.size == 2 }
        assertEquals(10L, h.viewModel.recentsUi.value.recordingByCallId[1]?.id)
        assertEquals(11L, h.viewModel.recentsUi.value.recordingByCallId[2]?.id)

        h.viewModel.setCallFilter(CallFilter.RECORDED)
        awaitUntil { h.viewModel.recentsUi.value.sections.sumOf { s -> s.groups.size } == 2 }

        assertEquals(listOf(10L), h.viewModel.recordingsFor(TestContacts.KIM_CHEOLSU).map { it.id })
        assertEquals(listOf(11L), h.viewModel.recordingsFor(TestContacts.LEE_CHEOLSU).map { it.id })

        h.viewModel.playRecording(recs[0], "김철수")
        assertEquals("김철수", h.viewModel.playing?.title)
        assertTrue(h.viewModel.onBack())
        assertNull(h.viewModel.playing)
    }

    @Test
    fun recordingsPermissionFlow() {
        start()
        h.viewModel.requestRecordingsPermission()
        awaitUntil { effects.any { it is UiEffect.RequestPermission && it.purpose == PermissionPurpose.RECORDINGS } }
        h.viewModel.onPermissionResult(PermissionPurpose.RECORDINGS, granted = false)
        assertEquals(false, h.recordings.lastPermissionResult)
        awaitUntil { effects.any { it is UiEffect.Message } }
    }
}
