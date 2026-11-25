package io.github.herfar.barodial.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.testing.FakeCallHistoryRepository
import io.github.herfar.barodial.testing.FakeCallRecordingsRepository
import io.github.herfar.barodial.testing.FakeContactsRepository
import io.github.herfar.barodial.testing.Harness
import io.github.herfar.barodial.testing.TestContacts
import io.github.herfar.barodial.ui.theme.BaroDialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppUiTest {

    // v1 규칙(UnconfinedTestDispatcher)을 유지한다. v2 로 바꾸면 동기화 방식이 달라진다.
    @Suppress("DEPRECATION")
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show(h: Harness) {
        rule.setContent { BaroDialTheme(dynamicColor = false) { BaroDialApp(h.viewModel, h.launcher) } }
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun startedActivity(): Intent? = shadowOf(rule.activity).nextStartedActivity

    /** 페이지 넘김 애니메이션이 끝날 때까지 시계를 진행한다. */
    private fun settlePager() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    private fun displayText(): String =
        rule.onNodeWithTag("dialDisplay", useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString("") { it.text }.orEmpty()

    private fun awaitDisplay(expected: String) {
        try {
            rule.waitUntil(3_000) { displayText() == expected }
        } catch (e: Throwable) {
            throw AssertionError("키패드 표시: 기대값='$expected' 실제='${displayText()}'", e)
        }
    }

    @Test
    fun searchBarStaysAtTheSameBottomPositionOnEveryTab() {
        show(Harness(app))
        val root = rule.onRoot().getBoundsInRoot()
        val positions = listOf("tab_HOME", "tab_CONTACTS", "tab_RECENTS", "tab_FAVORITES").map { tab ->
            rule.onNodeWithTag(tab).performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("searchBar").assertIsDisplayed()
            rule.onNodeWithTag("searchBar").getBoundsInRoot()
        }
        assertTrue("모든 탭에서 검색창 위치가 같아야 함", positions.distinct().size == 1)
        val bar = positions.first()
        assertTrue("검색창은 화면 아래쪽 1/4 안에 있어야 함", bar.top > root.bottom * 0.75f)
        assertTrue("검색창은 충분히 넓어야 함", (bar.right - bar.left) > (root.right - root.left) * 0.6f)
        assertTrue("검색창 높이는 56dp 이상", (bar.bottom - bar.top) >= 56.dp)
    }

    @Test
    fun choseongSearchThenCallHandsOffToDefaultPhoneApp() {
        show(Harness(app))
        rule.onNodeWithTag("searchBar").performClick()
        rule.onNodeWithTag("searchField").performTextInput("ㄱㅊㅅ")
        waitForTag("contactResult_1")
        rule.onNodeWithTag("contactResult_1").assertIsDisplayed()
        rule.onAllNodesWithTag("contactResult_2").assertCountEquals(0) // 김철민(ㄱㅊㅁ)은 제외

        rule.onNodeWithTag("callResult_1").performClick()
        rule.waitForIdle()
        val started = startedActivity()
        assertNotNull("발신 인텐트가 실행되어야 함", started)
        assertEquals(Intent.ACTION_DIAL, started!!.action)
        assertEquals("tel:010-1234-5678", started.data.toString())
        // 발신 후 검색이 닫히고 원래 화면(홈)으로 돌아온다
        rule.waitUntil(3_000) { rule.onAllNodesWithTag("searchResults").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("homeScreen").assertIsDisplayed()
    }

    @Test
    fun numberSearchWithoutHyphenFindsContact() {
        show(Harness(app))
        rule.onNodeWithTag("searchBar").performClick()
        rule.onNodeWithTag("searchField").performTextInput("0101234")
        waitForTag("contactResult_1")
        rule.onNodeWithTag("dialNumberRow").assertIsDisplayed()
    }

    @Test
    fun keypadInputFormattingBackspaceAndCall() {
        show(Harness(app))
        // 홈 탭이 곧 다이얼이다
        waitForTag("dialerPane")
        "01012345678".forEach { rule.onNodeWithTag("key_$it").performClick() }
        awaitDisplay("010-1234-5678")
        rule.onNodeWithTag("dialBackspace").performClick()
        awaitDisplay("010-123-4567")
        rule.onNodeWithTag("dialBackspace").performTouchInput { longClick() }
        awaitDisplay("")

        "0212345678".forEach { rule.onNodeWithTag("key_$it").performClick() }
        rule.onNodeWithTag("dialCallButton").performClick()
        rule.waitForIdle()
        val started = startedActivity()
        assertEquals(Intent.ACTION_DIAL, started?.action)
        assertEquals("tel:0212345678", started?.data.toString())
    }

    @Test
    fun withoutContactsPermissionTheAppExplainsAndKeypadStillWorks() {
        show(Harness(app, contacts = FakeContactsRepository(ContactsState.PermissionRequired)))
        rule.onNodeWithTag("contactsPermissionBanner").assertIsDisplayed()
        rule.onNodeWithTag("allowContactsBannerButton").performClick()
        rule.waitForIdle()
        val requested = shadowOf(rule.activity).lastRequestedPermission
        assertNotNull(requested)
        assertTrue(Manifest.permission.READ_CONTACTS in requested!!.requestedPermissions)

        waitForTag("dialerPane")
        "119".forEach { rule.onNodeWithTag("key_$it").performClick() }
        rule.onNodeWithTag("dialCallButton").performClick()
        rule.waitForIdle()
        assertEquals("tel:119", startedActivity()?.data.toString())
    }

    @Test
    fun recentsTabNeverShowsFakeCallLog() {
        show(Harness(app))
        rule.onNodeWithTag("tab_RECENTS").performClick()
        rule.onNodeWithTag("recentsExplanation").assertIsDisplayed()
        rule.onAllNodesWithTag("callLogList").assertCountEquals(0)
        // 두 번째 버튼: 삼성 전화(기본 전화 앱)의 최근기록 화면
        rule.onNodeWithTag("recentsSecondary").performClick()
        rule.waitForIdle()
        val started = startedActivity()
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(CallLog.Calls.CONTENT_TYPE, started?.type)
    }

    @Test
    fun callLogPermissionIsRequestedAndDeniedStateExplains() {
        val h = Harness(app)
        show(h)
        rule.onNodeWithTag("tab_RECENTS").performClick()
        settlePager()
        rule.onNodeWithTag("recentsPrimary").performClick()
        rule.waitForIdle()
        val requested = shadowOf(rule.activity).lastRequestedPermission
        assertTrue(Manifest.permission.READ_CALL_LOG in requested!!.requestedPermissions)
        // 파일로 설치해 Android 가 막은 경우: 이유 안내 + 삼성 전화로 보내는 버튼
        rule.runOnUiThread { h.callHistory.mutableAvailability.value = CallHistoryAvailability.Denied(Manifest.permission.READ_CALL_LOG) }
        rule.waitForIdle()
        rule.onNode(hasText("통화기록 권한이 막혀 있습니다")).assertExists()
        rule.onNodeWithTag("recentsPrimary").performClick()
        rule.waitForIdle()
        assertEquals(CallLog.Calls.CONTENT_TYPE, startedActivity()?.type)
    }

    @Test
    fun eachCallRowHasSmsButton() {
        val now = System.currentTimeMillis()
        val calls = listOf(CallLogEntry(7, "02-999-8888", "029998888", null, CallType.INCOMING, now - 60_000, 30))
        show(Harness(app, callHistory = FakeCallHistoryRepository(CallHistoryAvailability.Available, calls)))
        rule.onNodeWithTag("tab_RECENTS").performClick()
        settlePager()
        waitForTag("smsCall_7")
        rule.onNodeWithTag("smsCall_7").performClick()
        rule.waitForIdle()
        val started = startedActivity()
        assertEquals(Intent.ACTION_SENDTO, started?.action)
        assertEquals("smsto:02-999-8888", started?.data.toString())
    }

    @Test
    fun favoritesTapCallsLongPressOpensDetail() {
        val h = Harness(app)
        show(h)
        rule.onNodeWithTag("tab_FAVORITES").performClick()
        // 박영희는 연락처 앱 즐겨찾기(STARRED) → 번호가 2개이므로 탭하면 번호 선택 창
        waitForTag("favoriteTile_5")
        rule.onNodeWithTag("favoriteTile_5").performClick()
        waitForTag("numberPicker")
        rule.onNodeWithTag("pickNumber_01022223333").performClick()
        rule.waitForIdle()
        assertEquals("tel:+82 10-2222-3333", startedActivity()?.data?.let { "tel:" + it.schemeSpecificPart })

        rule.onNodeWithTag("favoriteTile_5").performTouchInput { longClick() }
        waitForTag("contactDetail")
        rule.onNodeWithTag("detailName").assertTextEquals("박영희")
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp-xhdpi")
    fun foldingAndUnfoldingKeepsSearchStateAndUsesTwoPanes() {
        var width by mutableStateOf(400.dp)
        val h = Harness(app)
        rule.setContent {
            BaroDialTheme(dynamicColor = false) {
                Box(Modifier.width(width).fillMaxHeight()) { BaroDialApp(h.viewModel, h.launcher) }
            }
        }
        rule.onNodeWithTag("searchBar").performClick()
        rule.onNodeWithTag("searchField").performTextInput("김철")
        waitForTag("contactResult_1")
        rule.onAllNodesWithTag("expandedLayout").assertCountEquals(0)

        // 펼침: 왼쪽 목록 + 오른쪽 키패드, 검색어 유지
        width = 840.dp
        rule.waitForIdle()
        rule.onNodeWithTag("expandedLayout").assertExists()
        rule.onNodeWithTag("dialerPane").assertExists()
        rule.onNode(hasTestTag("searchField") and hasText("김철")).assertExists()
        rule.onNodeWithTag("contactResult_1").assertIsDisplayed()

        // 펼친 상태에서 연락처를 누르면 오른쪽 창에 상세
        rule.onNodeWithTag("contactResult_1").performClick()
        waitForTag("contactDetail")
        rule.onNodeWithTag("contactResult_1").assertIsDisplayed() // 왼쪽 결과는 그대로

        // 다시 접기: 상세가 전체 화면으로, 뒤로 가면 검색 결과
        width = 400.dp
        rule.waitForIdle()
        rule.onNodeWithTag("contactDetail").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNode(hasTestTag("searchField") and hasText("김철")).assertExists()
        rule.onNodeWithTag("contactResult_1").assertIsDisplayed()
    }

    @Test
    fun bottomMenuOrderAndSwipeBetweenTabs() {
        val h = Harness(app)
        show(h)
        val lefts = listOf("tab_HOME", "tab_CONTACTS", "tab_RECENTS", "tab_FAVORITES").map { rule.onNodeWithTag(it).getBoundsInRoot().left }
        assertEquals("하단 메뉴 순서: 홈 · 연락처 · 최근기록 · 즐겨찾기", lefts.sorted(), lefts)

        val order = mutableListOf(h.viewModel.destination)
        repeat(3) { i ->
            rule.onNodeWithTag("tabPager").performTouchInput { swipeLeft() }
            settlePager()
            assertTrue("${i + 1}번째 스와이프 후에도 ${order.last()} 그대로", h.viewModel.destination != order.last())
            order += h.viewModel.destination
        }
        assertEquals(listOf(Destination.HOME, Destination.CONTACTS, Destination.RECENTS, Destination.FAVORITES), order)

        rule.onNodeWithTag("tabPager").performTouchInput { swipeRight() }
        settlePager()
        assertEquals(Destination.RECENTS, h.viewModel.destination)
        // 하단 메뉴를 눌러도 같은 페이지로 넘어간다
        rule.onNodeWithTag("tab_HOME").performClick()
        settlePager()
        assertEquals(Destination.HOME, h.viewModel.destination)
        rule.onNodeWithTag("dialerPane").assertIsDisplayed()
    }

    @Test
    fun copiedNumberCanBePastedIntoHomeDialer() {
        show(Harness(app))
        waitForTag("dialerPane")
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        rule.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("memo", "연락처: 010-2345-6789 (회사)")) }
        waitForTag("pasteChip")
        rule.onNodeWithTag("pasteChip").performClick()
        awaitDisplay("010-2345-6789")
        // 입력이 생기면 붙여넣기 버튼은 사라진다
        rule.waitUntil(3_000) { rule.onAllNodesWithTag("pasteChip").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun dialMenuOffersPasteAndPauses() {
        show(Harness(app))
        waitForTag("dialerPane")
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        rule.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("memo", "0212345678")) }
        rule.onNodeWithTag("dialMenu").performClick()
        rule.onNodeWithTag("dialMenuPaste").performClick()
        awaitDisplay("02-1234-5678")
        rule.onNodeWithTag("dialMenu").performClick()
        rule.onNode(hasText("2초간 대기 추가 (,)")).performClick()
        awaitDisplay("0212345678,")
    }

    @Test
    fun recordedCallsShowPlayButtonAndOpenPlayer() {
        val now = System.currentTimeMillis()
        val call = CallLogEntry(77, "010-1234-5678", "01012345678", null, CallType.INCOMING, now - 3_600_000, 180)
        val rec = CallRecording(5, "content://media/external/audio/media/5", "통화 녹음 김철수_251122_101010.m4a", "김철수", null, now - 3_600_000 + 20_000, 175_000, 1_000)
        val h = Harness(
            app,
            callHistory = FakeCallHistoryRepository(CallHistoryAvailability.Available, listOf(call)),
            recordings = FakeCallRecordingsRepository(RecordingsAvailability.Available, listOf(rec)),
        )
        show(h)
        rule.onNodeWithTag("tab_RECENTS").performClick()
        waitForTag("playCall_77")
        rule.onNodeWithTag("filter_RECORDED").assertIsDisplayed()
        rule.onNodeWithTag("playCall_77").performClick()
        rule.waitUntil(3_000) { h.viewModel.playing != null }
        assertEquals(5L, h.viewModel.playing?.recording?.id)
        waitForTag("recordingPlayer")
    }

    @Test
    fun callHistoryInDetailHasPlayButtonsAndUnknownNumbersOpenNumberDetail() {
        val now = System.currentTimeMillis()
        val calls = listOf(
            CallLogEntry(1, "010-1234-5678", "01012345678", null, CallType.INCOMING, now - 3_600_000, 180),
            CallLogEntry(2, "02-999-8888", "029998888", null, CallType.OUTGOING, now - 7_200_000, 90),
        )
        val recs = listOf(
            CallRecording(5, "content://media/external/audio/media/5", "통화 녹음 김철수_a.m4a", "김철수", null, now - 3_600_000 + 15_000, 170_000, 1),
            CallRecording(6, "content://media/external/audio/media/6", "통화 녹음 029998888_b.m4a", "029998888", "029998888", now - 7_200_000 + 9_000, 80_000, 1),
        )
        val h = Harness(
            app,
            callHistory = FakeCallHistoryRepository(CallHistoryAvailability.Available, calls),
            recordings = FakeCallRecordingsRepository(RecordingsAvailability.Available, recs),
        )
        show(h)
        rule.onNodeWithTag("tab_RECENTS").performClick()
        settlePager()
        // 연락처(김철수) → 상세의 통화내역 줄에 ▶
        rule.onNodeWithTag("callRow_1").performClick()
        waitForTag("contactDetail")
        rule.onNodeWithTag("contactDetail").performScrollToNode(hasTestTag("detailPlay_1"))
        rule.onNodeWithTag("detailPlay_1").performClick()
        rule.waitUntil(3_000) { h.viewModel.playing?.recording?.id == 5L }
        rule.runOnUiThread { h.viewModel.closePlayer(); h.viewModel.popRoute() }
        rule.waitForIdle()
        // 연락처에 없는 번호 → 번호 상세 (키패드가 아니라)
        rule.onNodeWithTag("callRow_2").performClick()
        waitForTag("numberDetail")
        rule.onNodeWithTag("numberDetailTitle").assertTextEquals("02-999-8888")
        rule.onNodeWithTag("numberDetail").performScrollToNode(hasTestTag("detailPlay_2"))
        rule.onNodeWithTag("detailPlay_2").performClick()
        rule.waitUntil(3_000) { h.viewModel.playing?.recording?.id == 6L }
    }

    @Test
    fun diagnosticsScreenOpensFromSettingsAndCanRescan() {
        val now = System.currentTimeMillis()
        val recs = listOf(CallRecording(1, "content://media/external/audio/media/1", "통화 녹음 김철수_251123_101010.m4a", "김철수", null, now, 60_000, 1, "Recordings/Call/", true))
        val h = Harness(app, recordings = FakeCallRecordingsRepository(RecordingsAvailability.Available, recs))
        show(h)
        rule.onNodeWithTag("settingsButton").performClick()
        waitForTag("settingsScreen")
        rule.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("settingsDiagnostics"))
        rule.onNodeWithTag("settingsDiagnostics").performClick()
        waitForTag("recordingDiagnostics")
        rule.onNode(hasText("통화 녹음 김철수_251123_101010.m4a")).assertExists()
        rule.onNodeWithTag("diagRescan").performClick()
        rule.waitUntil(3_000) { h.recordings.rescanCount == 1 }
    }

    @Test
    fun standardBuildListsRecordingFilesWithoutFakeCallLog() {
        val now = System.currentTimeMillis()
        val recs = listOf(
            CallRecording(1, "content://media/external/audio/media/1", "통화 녹음 김철수_251122_101010.m4a", "김철수", null, now - 7_200_000, 60_000, 1),
            CallRecording(2, "content://media/external/audio/media/2", "통화 녹음 0212345678_251121_090000.m4a", "0212345678", "0212345678", now - 90_000_000, 30_000, 1),
        )
        show(Harness(app, recordings = FakeCallRecordingsRepository(RecordingsAvailability.Available, recs)))
        rule.onNodeWithTag("tab_RECENTS").performClick()
        rule.onNodeWithTag("recentsExplanation").assertIsDisplayed()
        rule.onAllNodesWithTag("callLogList").assertCountEquals(0)
        rule.onNodeWithTag("recentsExplanation").performScrollToNode(hasTestTag("recording_2"))
        rule.onNodeWithTag("recording_1").assertExists()
        rule.onNodeWithTag("recording_2").assertExists()
    }

    @Test
    fun recordingsPermissionIsRequestedFromRecentsTab() {
        show(Harness(app))
        rule.onNodeWithTag("tab_RECENTS").performClick()
        rule.onNodeWithTag("recentsExplanation").performScrollToNode(hasTestTag("allowRecordingsButton"))
        rule.onNodeWithTag("allowRecordingsButton").performClick()
        rule.waitForIdle()
        val requested = shadowOf(rule.activity).lastRequestedPermission
        assertTrue(requested!!.requestedPermissions.contains("android.permission.READ_MEDIA_AUDIO"))
    }

    @Test
    fun fastScrollerStillJumpsOnVerticalDrag() {
        val syllables = listOf("가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타", "파", "하")
        var id = 500L
        val many = syllables.flatMap { sy ->
            (1..5).map { n -> TestContacts.contact(id++, "${sy}연락처$n", TestContacts.phone("010-%04d-%04d".format(id, n))) }
        } + TestContacts.contact(999, "1004 관리실", TestContacts.phone("02-100-4000"))
        val h = Harness(app, contacts = FakeContactsRepository.ready(many))
        show(h)
        rule.onNodeWithTag("tab_CONTACTS").performClick()
        settlePager()
        waitForTag("fastScroller")
        rule.onNodeWithTag("contactRow_500").assertIsDisplayed()
        // 스크롤 막대를 위에서 아래로 끌면 마지막 구간(#)으로 이동
        rule.onNodeWithTag("fastScroller").performTouchInput { swipeDown(startY = top + 5f, endY = bottom - 2f, durationMillis = 500) }
        rule.waitForIdle()
        rule.onNodeWithTag("contactRow_999").assertIsDisplayed()
        // 연락처 탭은 그대로 (가로 스와이프로 오인하지 않음)
        settlePager()
        assertEquals(Destination.CONTACTS, h.viewModel.destination)
    }

    // ---------------------------------------------------------------- 연락처에 추가

    private fun readyWith(vararg extra: io.github.herfar.barodial.data.contacts.Contact) =
        ContactsState.Ready.build(ContactsState.Ready.sortContacts(TestContacts.ALL + extra), FakeContactsRepository.engine)

    private fun sheetClosed() = rule.onAllNodesWithTag("addContactSheet").fetchSemanticsNodes().isEmpty()

    @Test
    fun addToContactsShowsSheetThenSavedNewContactIsShown() {
        val h = Harness(app)
        show(h)
        rule.runOnUiThread { h.viewModel.openNumber("02-999-8888") }
        waitForTag("numberDetail")
        rule.onNodeWithTag("numberDetailAddContact").performClick()

        // 바텀시트: 두 가지 선택지, 연락처 앱은 아직 열리지 않음
        waitForTag("addContactSheet")
        rule.onNodeWithTag("addContactNumber").assertTextEquals("02-999-8888")
        rule.onNodeWithTag("addContactNew").assertIsDisplayed()
        rule.onNodeWithTag("addContactExisting").assertIsDisplayed()
        assertEquals(null, shadowOf(rule.activity).nextStartedActivityForResult)

        rule.onNodeWithTag("addContactNew").performClick()
        rule.waitForIdle()
        val started = shadowOf(rule.activity).nextStartedActivityForResult
        assertNotNull("시스템 연락처 등록 화면을 결과 받기 방식으로 연다", started)
        assertEquals(Intent.ACTION_INSERT, started.intent.action)
        assertEquals(ContactsContract.Contacts.CONTENT_TYPE, started.intent.type)
        assertEquals("02-999-8888", started.intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        rule.waitUntil(3_000) { sheetClosed() }

        // 연락처 앱에서 저장하고 돌아옴 → 목록 갱신, 번호 상세 대신 새 연락처 상세
        val saved = TestContacts.contact(60, "새친구", TestContacts.phone("02-999-8888"))
        rule.runOnUiThread { h.contacts.set(readyWith(saved)) }
        shadowOf(rule.activity).receiveResult(started.intent, Activity.RESULT_OK, null)
        waitForTag("contactDetail")
        rule.onNodeWithTag("detailName").assertTextEquals("새친구")
        rule.onAllNodesWithTag("numberDetail").assertCountEquals(0)
        rule.onNodeWithText("새친구 연락처에 저장했습니다").assertExists()

        // 뒤로 가면 원래 탭, 연락처 탭 목록에도 새 연락처가 보인다
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("tab_CONTACTS").performClick()
        settlePager()
        rule.onNodeWithTag("contactsList").performScrollToNode(hasTestTag("contactRow_60"))
        rule.onNodeWithTag("contactRow_60").assertIsDisplayed()
    }

    @Test
    fun cancellingTheContactsAppReturnsToNumberDetail() {
        val h = Harness(app)
        show(h)
        rule.runOnUiThread { h.viewModel.openNumber("02-999-8888") }
        waitForTag("numberDetail")
        rule.onNodeWithTag("numberDetailAddContact").performClick()
        waitForTag("addContactSheet")
        rule.onNodeWithTag("addContactNew").performClick()
        rule.waitForIdle()
        val started = shadowOf(rule.activity).nextStartedActivityForResult
        shadowOf(rule.activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        rule.waitForIdle()
        rule.onNodeWithTag("numberDetail").assertIsDisplayed()
        rule.onNodeWithTag("numberDetailTitle").assertTextEquals("02-999-8888")
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp-xhdpi")
    fun addToExistingContactWorksUnfoldedAndAfterFolding() {
        var width by mutableStateOf(840.dp)
        val h = Harness(app)
        rule.setContent {
            BaroDialTheme(dynamicColor = false) {
                Box(Modifier.width(width).fillMaxHeight()) { BaroDialApp(h.viewModel, h.launcher) }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("expandedLayout").assertExists()
        // 펼친 화면: 오른쪽 창에 번호 상세 → 연락처에 추가 → 시트
        rule.runOnUiThread { h.viewModel.openNumber("02-999-8888") }
        waitForTag("numberDetail")
        rule.onNodeWithTag("numberDetailAddContact").performClick()
        waitForTag("addContactSheet")

        // 시트가 떠 있는 채로 접어도 시트와 번호가 유지된다
        width = 400.dp
        rule.waitForIdle()
        rule.onAllNodesWithTag("expandedLayout").assertCountEquals(0)
        rule.onNodeWithTag("addContactNumber").assertTextEquals("02-999-8888")

        rule.onNodeWithTag("addContactExisting").performClick()
        rule.waitForIdle()
        val started = shadowOf(rule.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_INSERT_OR_EDIT, started.intent.action)
        assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, started.intent.type)
        assertEquals("02-999-8888", started.intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))

        // 이철수 연락처에 번호를 추가하고 돌아옴 (연락처 앱이 돌려준 URI 로 찾는다)
        val lee = TestContacts.LEE_CHEOLSU
        val updated = lee.copy(phones = lee.phones + TestContacts.phone("02-999-8888"))
        rule.runOnUiThread {
            h.contacts.set(
                ContactsState.Ready.build(
                    ContactsState.Ready.sortContacts(TestContacts.ALL.map { if (it.id == lee.id) updated else it }),
                    FakeContactsRepository.engine,
                ),
            )
        }
        val result = Intent().setData(Uri.parse("content://com.android.contacts/contacts/lookup/${lee.lookupKey}/${lee.id}"))
        shadowOf(rule.activity).receiveResult(started.intent, Activity.RESULT_OK, result)
        waitForTag("contactDetail")
        rule.onNodeWithTag("detailName").assertTextEquals("이철수")

        // 다시 펼치면 오른쪽 창에 같은 연락처 상세
        width = 840.dp
        rule.waitForIdle()
        rule.onNodeWithTag("expandedLayout").assertExists()
        rule.onNodeWithTag("detailName").assertTextEquals("이철수")
    }
}
