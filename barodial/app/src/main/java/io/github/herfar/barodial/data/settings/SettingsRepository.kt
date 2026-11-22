package io.github.herfar.barodial.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 앱을 열었을 때 처음 보여줄 탭 */
enum class StartTab { HOME, CONTACTS, FAVORITES }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** 실험적: CALL_PHONE 권한으로 ACTION_CALL 원터치 발신. 기본값은 끔(ACTION_DIAL). */
    val directCall: Boolean = false,
    /** 검색 결과 중 가장 관련 높은 항목을 하단 검색창 바로 위에 표시 */
    val resultsNearSearchBar: Boolean = true,
    val showSystemFavorites: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val clearSearchAfterCall: Boolean = true,
    val keypadHaptics: Boolean = true,
    val startTab: StartTab = StartTab.HOME,
    /** 연락처 권한을 한 번이라도 요청했는지 (영구 거부 판별용) */
    val contactsPermissionRequested: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val DIRECT_CALL = booleanPreferencesKey("direct_call")
        val NEAR_BAR = booleanPreferencesKey("results_near_search_bar")
        val SYSTEM_FAVORITES = booleanPreferencesKey("show_system_favorites")
        val SAVE_HISTORY = booleanPreferencesKey("save_search_history")
        val CLEAR_AFTER_CALL = booleanPreferencesKey("clear_search_after_call")
        val HAPTICS = booleanPreferencesKey("keypad_haptics")
        val START_TAB = stringPreferencesKey("start_tab")
        val CONTACTS_REQUESTED = booleanPreferencesKey("contacts_permission_requested")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.THEME] = next.themeMode.name
            prefs[Keys.DYNAMIC] = next.dynamicColor
            prefs[Keys.DIRECT_CALL] = next.directCall
            prefs[Keys.NEAR_BAR] = next.resultsNearSearchBar
            prefs[Keys.SYSTEM_FAVORITES] = next.showSystemFavorites
            prefs[Keys.SAVE_HISTORY] = next.saveSearchHistory
            prefs[Keys.CLEAR_AFTER_CALL] = next.clearSearchAfterCall
            prefs[Keys.HAPTICS] = next.keypadHaptics
            prefs[Keys.START_TAB] = next.startTab.name
            prefs[Keys.CONTACTS_REQUESTED] = next.contactsPermissionRequested
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = this[Keys.THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.themeMode,
            dynamicColor = this[Keys.DYNAMIC] ?: d.dynamicColor,
            directCall = this[Keys.DIRECT_CALL] ?: d.directCall,
            resultsNearSearchBar = this[Keys.NEAR_BAR] ?: d.resultsNearSearchBar,
            showSystemFavorites = this[Keys.SYSTEM_FAVORITES] ?: d.showSystemFavorites,
            saveSearchHistory = this[Keys.SAVE_HISTORY] ?: d.saveSearchHistory,
            clearSearchAfterCall = this[Keys.CLEAR_AFTER_CALL] ?: d.clearSearchAfterCall,
            keypadHaptics = this[Keys.HAPTICS] ?: d.keypadHaptics,
            startTab = this[Keys.START_TAB]?.let { v -> StartTab.entries.firstOrNull { it.name == v } } ?: d.startTab,
            contactsPermissionRequested = this[Keys.CONTACTS_REQUESTED] ?: d.contactsPermissionRequested,
        )
    }
}
