package io.github.herfar.barodial

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.herfar.barodial.data.calllog.CallHistoryModule
import io.github.herfar.barodial.data.calllog.CallHistoryRepository
import io.github.herfar.barodial.data.contacts.AndroidContactsRepository
import io.github.herfar.barodial.data.contacts.ContactsRepository
import io.github.herfar.barodial.data.favorites.DataStoreFavoritesRepository
import io.github.herfar.barodial.data.favorites.FavoritesRepository
import io.github.herfar.barodial.data.history.DataStoreSearchHistoryRepository
import io.github.herfar.barodial.data.history.SearchHistoryRepository
import io.github.herfar.barodial.data.recordings.CallRecordingsRepository
import io.github.herfar.barodial.data.recordings.MediaStoreCallRecordingsRepository
import io.github.herfar.barodial.data.search.ContactSearchEngine
import io.github.herfar.barodial.data.settings.DataStoreSettingsRepository
import io.github.herfar.barodial.data.settings.SettingsRepository
import io.github.herfar.barodial.dial.DialerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 앱 전역 의존성 (수동 DI). 테스트에서는 가짜 구현으로 바꿔 끼운다. */
interface AppContainer {
    val searchEngine: ContactSearchEngine
    val contactsRepository: ContactsRepository
    val favoritesRepository: FavoritesRepository
    val searchHistoryRepository: SearchHistoryRepository
    val settingsRepository: SettingsRepository
    val callHistoryRepository: CallHistoryRepository
    val callRecordingsRepository: CallRecordingsRepository
    val dialerLauncher: DialerLauncher
}

/** 앱 데이터는 이 파일 하나(기기 내부 저장소)에만 저장된다. */
private val Context.appDataStore by preferencesDataStore(name = "barodial")

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val searchEngine = ContactSearchEngine()
    override val contactsRepository: ContactsRepository by lazy {
        AndroidContactsRepository(appContext, appScope, searchEngine)
    }
    override val favoritesRepository: FavoritesRepository by lazy { DataStoreFavoritesRepository(appContext.appDataStore) }
    override val searchHistoryRepository: SearchHistoryRepository by lazy {
        DataStoreSearchHistoryRepository(appContext.appDataStore)
    }
    override val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext.appDataStore) }
    override val callHistoryRepository: CallHistoryRepository by lazy { CallHistoryModule.create(appContext, appScope) }
    override val callRecordingsRepository: CallRecordingsRepository by lazy {
        MediaStoreCallRecordingsRepository(appContext, appScope)
    }
    override val dialerLauncher: DialerLauncher by lazy { DialerLauncher(appContext) }
}
