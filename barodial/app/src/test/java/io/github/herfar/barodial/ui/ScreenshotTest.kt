package io.github.herfar.barodial.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.favorites.FavoriteEntry
import io.github.herfar.barodial.data.settings.ThemeMode
import io.github.herfar.barodial.testing.FakeContactsRepository
import io.github.herfar.barodial.testing.Harness
import io.github.herfar.barodial.testing.InMemoryFavoritesRepository
import io.github.herfar.barodial.testing.TestContacts
import io.github.herfar.barodial.ui.theme.BaroDialTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 화면 캡처 (docs/screenshots). 테스트 전용 예시 연락처를 사용한다.
 * 실행: gradlew testStandardDebugUnitTest --tests "*ScreenshotTest*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    // v1 규칙(UnconfinedTestDispatcher)을 유지한다. v2 로 바꾸면 동기화 방식이 달라진다.
    @Suppress("DEPRECATION")
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val outDir = File(System.getProperty("barodial.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private val sampleContacts = TestContacts.ALL + listOf(
        TestContacts.contact(21, "강민준", TestContacts.phone("010-2345-6789")),
        TestContacts.contact(22, "나은지", TestContacts.phone("010-3456-7890"), organization = "바로디자인"),
        TestContacts.contact(23, "도서관 안내", TestContacts.phone("02-2100-3000", 3)),
        TestContacts.contact(24, "문지우", TestContacts.phone("010-4567-8901")),
        TestContacts.contact(25, "서하은", TestContacts.phone("010-5678-9012")),
        TestContacts.contact(26, "엄마", TestContacts.phone("010-1111-2222"), starred = true),
        TestContacts.contact(27, "정도윤", TestContacts.phone("010-6789-0123")),
        TestContacts.contact(28, "최예린", TestContacts.phone("010-7890-1234")),
        TestContacts.contact(29, "한빛치과", TestContacts.phone("031-123-4567", 3)),
        TestContacts.contact(30, "Bob Lee", TestContacts.phone("+1 650-555-0100")),
    )

    private fun harness(): Harness {
        val favorites = InMemoryFavoritesRepository(
            listOf(
                FavoriteEntry("lookup-1", 1, "김철수"),
                FavoriteEntry("lookup-22", 22, "나은지"),
                FavoriteEntry("lookup-9", 9, "왕서방"),
            ),
        )
        val h = Harness(app, contacts = FakeContactsRepository.ready(sampleContacts), favorites = favorites)
        runBlocking {
            h.history.recordContact("lookup-24", 24, "문지우", "010-4567-8901")
            h.history.recordContact("lookup-24", 24, "문지우", "010-4567-8901")
            h.history.recordNumber("02-555-7777", dialed = true)
            h.history.recordContact("lookup-3", 3, "이철수", null)
        }
        return h
    }

    private fun show(h: Harness, theme: ThemeMode = ThemeMode.LIGHT) {
        rule.setContent { BaroDialTheme(themeMode = theme, dynamicColor = false) { BaroDialApp(h.viewModel, h.launcher) } }
        rule.waitForIdle()
    }

    private fun settle(tag: String? = null) {
        if (tag != null) rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        settle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun search(h: Harness, text: String) {
        rule.runOnUiThread {
            h.viewModel.onSearchFocused()
            h.viewModel.searchField.setTextAndPlaceCursorAtEnd(text)
        }
    }

    @Test
    @Config(qualifiers = "ko-rKR-w384dp-h832dp-xxhdpi")
    fun foldedLight() {
        val h = harness()
        show(h)
        settle("homeScreen")
        capture("folded_01_home")

        search(h, "ㄱㅊ")
        settle("contactResult_1")
        capture("folded_02_search_choseong")

        rule.runOnUiThread { h.viewModel.searchField.setTextAndPlaceCursorAtEnd("010") }
        settle("dialNumberRow")
        capture("folded_03_search_number")

        rule.runOnUiThread { h.viewModel.exitSearch(); h.viewModel.selectDestination(Destination.CONTACTS) }
        settle("contactsList")
        capture("folded_04_contacts")

        rule.runOnUiThread { h.viewModel.openContact(TestContacts.PARK_YOUNGHEE) }
        settle("contactDetail")
        capture("folded_05_detail")

        rule.runOnUiThread { h.viewModel.popRoute(); h.viewModel.openKeypad("01012345") }
        settle("dialerPane")
        capture("folded_06_keypad")

        rule.runOnUiThread { h.viewModel.popRoute(); h.viewModel.selectDestination(Destination.FAVORITES) }
        settle("favoritesGrid")
        capture("folded_07_favorites")

        rule.runOnUiThread { h.viewModel.selectDestination(Destination.RECENTS) }
        settle("recentsExplanation")
        capture("folded_08_recents")

        rule.runOnUiThread { h.viewModel.openSettings() }
        settle("settingsScreen")
        capture("folded_09_settings")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w384dp-h832dp-xxhdpi")
    fun foldedDark() {
        val h = harness()
        show(h, ThemeMode.DARK)
        settle("homeScreen")
        capture("folded_dark_01_home")
        search(h, "김처")
        settle("contactResult_1")
        capture("folded_dark_02_search")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w384dp-h832dp-xxhdpi")
    fun foldedNoPermission() {
        val h = Harness(app, contacts = FakeContactsRepository(io.github.herfar.barodial.data.contacts.ContactsState.PermissionRequired))
        show(h)
        settle("contactsPermissionCard")
        capture("folded_10_no_permission")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w820dp-h900dp-xhdpi")
    fun unfolded() {
        val h = harness()
        show(h)
        settle("expandedLayout")
        capture("unfolded_01_home_keypad")

        rule.runOnUiThread { h.viewModel.selectDestination(Destination.CONTACTS) }
        settle("contactsList")
        rule.runOnUiThread { h.viewModel.openContact(TestContacts.KIM_CHEOLSU) }
        settle("contactDetail")
        capture("unfolded_02_contacts_detail")

        search(h, "한국전력")
        settle("contactResult_4")
        capture("unfolded_03_search")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w384dp-h832dp-xxhdpi", fontScale = 1.3f)
    fun foldedLargeFont() {
        val h = harness()
        show(h)
        settle("homeScreen")
        capture("folded_11_large_font_home")
        rule.runOnUiThread { h.viewModel.openKeypad() }
        settle("dialerPane")
        capture("folded_12_large_font_keypad")
    }
}
