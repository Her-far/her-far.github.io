package io.github.herfar.barodial.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.github.herfar.barodial.data.favorites.DataStoreFavoritesRepository
import io.github.herfar.barodial.data.favorites.FavoriteEntry
import io.github.herfar.barodial.data.history.DataStoreSearchHistoryRepository
import io.github.herfar.barodial.data.history.HistoryKind
import io.github.herfar.barodial.data.settings.DataStoreSettingsRepository
import io.github.herfar.barodial.data.settings.StartTab
import io.github.herfar.barodial.data.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 실제 DataStore 파일에 저장한 뒤, 새 DataStore 인스턴스(= 앱 재시작)로 다시 읽어 값이 유지되는지 확인한다.
 */
class DataStoreRepositoriesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun <T> withStore(file: File, block: suspend (DataStore<Preferences>) -> T): T = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun settingsSurviveRestart() {
        val file = File(tmp.root, "settings.preferences_pb")
        withStore(file) { store ->
            val repo = DataStoreSettingsRepository(store)
            assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
            repo.update { it.copy(themeMode = ThemeMode.DARK, directCall = true, startTab = StartTab.CONTACTS, resultsNearSearchBar = false) }
        }
        withStore(file) { store ->
            val s = DataStoreSettingsRepository(store).settings.first()
            assertEquals(ThemeMode.DARK, s.themeMode)
            assertTrue(s.directCall)
            assertEquals(StartTab.CONTACTS, s.startTab)
            assertEquals(false, s.resultsNearSearchBar)
        }
    }

    @Test
    fun favoritesKeepOrderAndPreferredNumbers() {
        val file = File(tmp.root, "fav.preferences_pb")
        withStore(file) { store ->
            val repo = DataStoreFavoritesRepository(store)
            repo.add(FavoriteEntry("a\tkey", 1, "가나"))
            repo.add(FavoriteEntry("b", 2, "다라"))
            repo.add(FavoriteEntry("c", 3, "마바"))
            repo.add(FavoriteEntry("b", 2, "다라")) // 중복은 무시
            repo.move("c", -2)
            repo.setPreferredNumber("b", "023334444")
        }
        withStore(file) { store ->
            val repo = DataStoreFavoritesRepository(store)
            assertEquals(listOf("c", "a\tkey", "b"), repo.favorites.first().map { it.lookupKey })
            assertEquals(mapOf("b" to "023334444"), repo.preferredNumbers.first())
            repo.remove("a\tkey")
            repo.setPreferredNumber("b", null)
            assertEquals(listOf("c", "b"), repo.favorites.first().map { it.lookupKey })
            assertTrue(repo.preferredNumbers.first().isEmpty())
        }
    }

    @Test
    fun historyRecordsDialCountsAndCanBeCleared() {
        val file = File(tmp.root, "history.preferences_pb")
        var now = 1_000L
        withStore(file) { store ->
            val repo = DataStoreSearchHistoryRepository(store, clock = { now++ })
            repo.recordContact("kim", 1, "김철수", dialedNumber = null)
            repo.recordContact("kim", 1, "김철수", dialedNumber = "010-1234-5678")
            repo.recordNumber("02-123-4567", dialed = true)
        }
        withStore(file) { store ->
            val repo = DataStoreSearchHistoryRepository(store, clock = { now++ })
            val entries = repo.entries.first()
            assertEquals(listOf(HistoryKind.NUMBER, HistoryKind.CONTACT), entries.map { it.kind })
            assertEquals("021234567", entries[0].key)
            assertEquals(1, entries[1].dialCount)
            assertEquals("02-123-4567", repo.lastDialedNumber.first())
            repo.clear()
            assertTrue(repo.entries.first().isEmpty())
            assertNull(repo.lastDialedNumber.first())
        }
    }

    @Test
    fun historyIsCapped() {
        val file = File(tmp.root, "cap.preferences_pb")
        withStore(file) { store ->
            val repo = DataStoreSearchHistoryRepository(store, maxEntries = 5)
            repeat(8) { repo.recordNumber("0101234000$it", dialed = false) }
            assertEquals(5, repo.entries.first().size)
        }
    }
}
