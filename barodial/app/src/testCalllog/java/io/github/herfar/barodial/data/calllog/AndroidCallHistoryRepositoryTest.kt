package io.github.herfar.barodial.data.calllog

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.CallLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

class FakeCallLogProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        queries++
        lastUri = uri
        val cols = projection!!
        return MatrixCursor(cols).apply { rows.forEach { r -> addRow(cols.map { r[it] }.toTypedArray()) } }
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        var queries = 0
        var lastUri: Uri? = null
        val rows = mutableListOf<Map<String, Any?>>()
    }
}

@RunWith(AndroidJUnit4::class)
class AndroidCallHistoryRepositoryTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun row(id: Long, number: String, name: String?, type: Int, date: Long, duration: Long) = mapOf(
        CallLog.Calls._ID to id,
        CallLog.Calls.NUMBER to number,
        CallLog.Calls.CACHED_NAME to name,
        CallLog.Calls.TYPE to type,
        CallLog.Calls.DATE to date,
        CallLog.Calls.DURATION to duration,
    )

    @Before
    fun setUp() {
        FakeCallLogProvider.queries = 0
        FakeCallLogProvider.rows.clear()
        FakeCallLogProvider.rows += listOf(
            row(1, "010-1234-5678", "김철수", CallLog.Calls.MISSED_TYPE, 3_000, 0),
            row(2, "+82 2-555-1234", null, CallLog.Calls.OUTGOING_TYPE, 2_000, 125),
            row(3, "", null, CallLog.Calls.BLOCKED_TYPE, 1_000, 0),
        )
        Robolectric.buildContentProvider(FakeCallLogProvider::class.java).create(CallLog.AUTHORITY)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) throw AssertionError("시간 초과")
            Thread.sleep(10)
        }
    }

    @Test
    fun withoutPermissionNothingIsReadAndNoFakeEntries() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG)
        val repo = AndroidCallHistoryRepository(app, scope)
        await { repo.availability.value is CallHistoryAvailability.PermissionRequired }
        assertTrue(repo.entries.value.isEmpty())
        assertEquals(0, FakeCallLogProvider.queries)

        // hard-restricted 권한은 대화상자 없이 거부될 수 있다 → Denied 로 안내
        repo.onPermissionResult(granted = false)
        await { repo.availability.value is CallHistoryAvailability.Denied }
        assertTrue(repo.entries.value.isEmpty())
    }

    @Test
    fun withPermissionEntriesAreMapped() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG)
        val repo = AndroidCallHistoryRepository(app, scope, limit = 100)
        await { repo.entries.value.size == 3 }
        assertEquals(CallHistoryAvailability.Available, repo.availability.value)
        val e = repo.entries.value
        assertEquals(CallType.MISSED, e[0].type)
        assertEquals("김철수", e[0].cachedName)
        assertEquals("01012345678", e[0].nationalDigits)
        assertEquals(CallType.OUTGOING, e[1].type)
        assertEquals("025551234", e[1].nationalDigits)
        assertEquals(125L, e[1].durationSeconds)
        assertEquals(CallType.BLOCKED, e[2].type)
        assertEquals("100", FakeCallLogProvider.lastUri?.getQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY))
    }
}
