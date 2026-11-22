package io.github.herfar.barodial.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.contacts.AndroidContactsRepository
import io.github.herfar.barodial.data.contacts.ContactsLoader
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.search.ContactSearchEngine
import io.github.herfar.barodial.testing.awaitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** Contacts Provider 의 Data 테이블 응답을 흉내 내는 테스트용 Provider */
class FakeContactsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cols = projection ?: ContactsLoader.PROJECTION
        val cursor = MatrixCursor(cols)
        for (row in rows) cursor.addRow(cols.map { row[it] }.toTypedArray())
        queries++
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        var queries = 0
        val rows = mutableListOf<Map<String, Any?>>()

        fun row(id: Long, contactId: Long, name: String?, mime: String, data1: String?, data2: Int? = null, data4: String? = null, superPrimary: Int = 0, starred: Int = 0) =
            mapOf(
                ContactsContract.Data._ID to id,
                ContactsContract.Data.CONTACT_ID to contactId,
                ContactsContract.Data.LOOKUP_KEY to "lk$contactId",
                ContactsContract.Data.DISPLAY_NAME_PRIMARY to name,
                ContactsContract.Data.PHOTO_THUMBNAIL_URI to null,
                ContactsContract.Data.PHOTO_URI to null,
                ContactsContract.Data.STARRED to starred,
                ContactsContract.Data.MIMETYPE to mime,
                ContactsContract.Data.DATA1 to data1,
                ContactsContract.Data.DATA2 to data2,
                ContactsContract.Data.DATA3 to null,
                ContactsContract.Data.DATA4 to data4,
                ContactsContract.Data.IS_SUPER_PRIMARY to superPrimary,
            )
    }
}

@RunWith(AndroidJUnit4::class)
class AndroidContactsRepositoryTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Before
    fun setUp() {
        FakeContactsProvider.rows.clear()
        FakeContactsProvider.queries = 0
        FakeContactsProvider.rows += listOf(
            // 김철수: 같은 번호가 두 계정에 저장됨(+82 / 국내형식) → 하나로 합쳐야 함, 두 번째가 기본 번호
            FakeContactsProvider.row(1, 1, "김철수", Phone.CONTENT_ITEM_TYPE, "+82 10-1234-5678", Phone.TYPE_MOBILE, "+821012345678"),
            FakeContactsProvider.row(2, 1, "김철수", Phone.CONTENT_ITEM_TYPE, "010-1234-5678", Phone.TYPE_MOBILE, superPrimary = 1),
            FakeContactsProvider.row(3, 1, "김철수", Organization.CONTENT_ITEM_TYPE, "한국전력공사", data4 = "과장"),
            FakeContactsProvider.row(4, 1, "김철수", Nickname.CONTENT_ITEM_TYPE, "철수씨"),
            FakeContactsProvider.row(5, 1, "김철수", Phone.CONTENT_ITEM_TYPE, "02-555-1234", Phone.TYPE_WORK),
            // 번호 없는 연락처 → 제외
            FakeContactsProvider.row(6, 2, "번호없음", Organization.CONTENT_ITEM_TYPE, "회사"),
            // 이름 없는 연락처 → 번호를 이름으로
            FakeContactsProvider.row(7, 3, null, Phone.CONTENT_ITEM_TYPE, "01099998888", Phone.TYPE_MOBILE, starred = 1),
        )
        Robolectric.buildContentProvider(FakeContactsProvider::class.java).create(ContactsContract.AUTHORITY)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun loaderMergesRowsIntoContacts() {
        val contacts = ContactsLoader.load(app.contentResolver)
        assertEquals(2, contacts.size)
        val kim = contacts.first { it.id == 1L }
        assertEquals("김철수", kim.displayName)
        assertEquals(listOf("01012345678", "025551234"), kim.phones.map { it.nationalDigits })
        assertTrue("중복 번호 중 기본 번호 표시 유지", kim.phones[0].isDefault)
        assertEquals("한국전력공사", kim.organization)
        assertEquals("과장", kim.jobTitle)
        assertEquals(listOf("철수씨"), kim.nicknames)
        assertFalse(kim.starred)

        val unnamed = contacts.first { it.id == 3L }
        assertEquals("010-9999-8888", unnamed.displayName)
        assertTrue(unnamed.starred)
    }

    @Test
    fun repositoryRespectsPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        val repo = AndroidContactsRepository(app, scope, ContactSearchEngine())
        awaitUntil { repo.state.value == ContactsState.PermissionRequired }
        assertEquals("권한 없이 Provider 를 읽으면 안 됨", 0, FakeContactsProvider.queries)

        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        repo.refreshIfNeeded()
        awaitUntil { repo.state.value is ContactsState.Ready }
        val ready = repo.state.value as ContactsState.Ready
        assertEquals(2, ready.contacts.size)
        assertEquals("김철수", ready.findByNumber("01012345678")?.displayName)
        assertEquals("김철수", ready.find("lk1", null)?.displayName)
        assertNull(ready.find("unknown", 999))

        // 권한을 다시 빼면 목록을 비운다
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        repo.refreshIfNeeded()
        awaitUntil { repo.state.value == ContactsState.PermissionRequired }
    }
}
