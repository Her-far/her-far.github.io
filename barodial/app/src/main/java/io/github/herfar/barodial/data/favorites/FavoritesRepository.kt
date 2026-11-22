package io.github.herfar.barodial.data.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.herfar.barodial.core.util.ListCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 앱 자체 즐겨찾기 항목. 시스템 연락처의 "즐겨찾기(STARRED)"와는 별개로 기기에만 저장된다.
 * 시스템 즐겨찾기를 바꾸려면 WRITE_CONTACTS 권한이 필요하므로 앱에서는 읽기만 한다.
 */
data class FavoriteEntry(
    val lookupKey: String,
    val contactId: Long,
    val displayName: String,
)

interface FavoritesRepository {
    /** 사용자가 정한 순서 */
    val favorites: Flow<List<FavoriteEntry>>

    /** 연락처별로 "항상 이 번호로 걸기"를 지정한 번호 (lookupKey → 국내 숫자 형식) */
    val preferredNumbers: Flow<Map<String, String>>

    suspend fun add(entry: FavoriteEntry)
    suspend fun remove(lookupKey: String)

    /** [offset] 만큼 위(-) 또는 아래(+)로 옮긴다. */
    suspend fun move(lookupKey: String, offset: Int)
    suspend fun setPreferredNumber(lookupKey: String, nationalDigits: String?)
}

class DataStoreFavoritesRepository(private val dataStore: DataStore<Preferences>) : FavoritesRepository {

    private val keyFavorites = stringPreferencesKey("favorites_v1")
    private val keyPreferred = stringPreferencesKey("preferred_numbers_v1")

    override val favorites: Flow<List<FavoriteEntry>> = dataStore.data.map { decodeFavorites(it[keyFavorites]) }

    override val preferredNumbers: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        ListCodec.decode(prefs[keyPreferred])
            .filter { it.size >= 2 && it[0].isNotEmpty() && it[1].isNotEmpty() }
            .associate { it[0] to it[1] }
    }

    override suspend fun add(entry: FavoriteEntry) = editFavorites { list ->
        if (list.any { it.lookupKey == entry.lookupKey }) list else list + entry
    }

    override suspend fun remove(lookupKey: String) = editFavorites { list -> list.filterNot { it.lookupKey == lookupKey } }

    override suspend fun move(lookupKey: String, offset: Int) = editFavorites { list ->
        val from = list.indexOfFirst { it.lookupKey == lookupKey }
        if (from < 0) return@editFavorites list
        val to = (from + offset).coerceIn(0, list.lastIndex)
        if (to == from) return@editFavorites list
        list.toMutableList().apply { add(to, removeAt(from)) }
    }

    override suspend fun setPreferredNumber(lookupKey: String, nationalDigits: String?) {
        dataStore.edit { prefs ->
            val map = ListCodec.decode(prefs[keyPreferred])
                .filter { it.size >= 2 }
                .associate { it[0] to it[1] }
                .toMutableMap()
            if (nationalDigits.isNullOrEmpty()) map.remove(lookupKey) else map[lookupKey] = nationalDigits
            prefs[keyPreferred] = ListCodec.encode(map.map { (k, v) -> listOf(k, v) })
        }
    }

    private suspend fun editFavorites(transform: (List<FavoriteEntry>) -> List<FavoriteEntry>) {
        dataStore.edit { prefs ->
            val next = transform(decodeFavorites(prefs[keyFavorites]))
            prefs[keyFavorites] = ListCodec.encode(next.map { listOf(it.lookupKey, it.contactId.toString(), it.displayName) })
        }
    }

    private fun decodeFavorites(text: String?): List<FavoriteEntry> =
        ListCodec.decode(text).mapNotNull { row ->
            if (row.size < 3 || row[0].isEmpty()) return@mapNotNull null
            FavoriteEntry(row[0], row[1].toLongOrNull() ?: -1L, row[2])
        }
}
