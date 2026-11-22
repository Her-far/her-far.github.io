package io.github.herfar.barodial.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.provider.CallLog
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.testing.FakeContactsRepository
import io.github.herfar.barodial.testing.Harness
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
        val positions = listOf("tab_HOME", "tab_CONTACTS", "tab_FAVORITES", "tab_RECENTS").map { tab ->
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
        rule.waitUntil(3_000) { rule.onAllNodesWithTag("homeScreen").fetchSemanticsNodes().isNotEmpty() }
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
        rule.onNodeWithTag("keypadButton").performClick()
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
        rule.onNodeWithTag("contactsPermissionCard").assertIsDisplayed()
        rule.onNodeWithTag("allowContactsButton").performClick()
        rule.waitForIdle()
        val requested = shadowOf(rule.activity).lastRequestedPermission
        assertNotNull(requested)
        assertTrue(Manifest.permission.READ_CONTACTS in requested!!.requestedPermissions)

        rule.onNodeWithTag("keypadButton").performClick()
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
        rule.onNodeWithTag("recentsPrimary").performClick()
        rule.waitForIdle()
        val started = startedActivity()
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(CallLog.Calls.CONTENT_TYPE, started?.type)
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
}
