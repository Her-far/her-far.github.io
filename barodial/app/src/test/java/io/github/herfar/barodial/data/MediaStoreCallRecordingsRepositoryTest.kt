package io.github.herfar.barodial.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.recordings.MediaStoreCallRecordingsRepository
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.testing.awaitUntil
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

class FakeMediaProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        queries++
        lastSelectionArgs = selectionArgs?.toList()
        val cols = projection!!
        return MatrixCursor(cols).apply { rows.forEach { r -> addRow(cols.map { r[it] }.toTypedArray()) } }
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        var queries = 0
        var lastSelectionArgs: List<String>? = null
        val rows = mutableListOf<Map<String, Any?>>()
    }
}

@RunWith(AndroidJUnit4::class)
class MediaStoreCallRecordingsRepositoryTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun row(id: Long, name: String, modifiedSec: Long, durationMs: Long) = mapOf(
        MediaStore.Audio.Media._ID to id,
        MediaStore.Audio.Media.DISPLAY_NAME to name,
        MediaStore.Audio.Media.RELATIVE_PATH to "Recordings/Call/",
        MediaStore.Audio.Media.DATE_MODIFIED to modifiedSec,
        MediaStore.Audio.Media.DURATION to durationMs,
        MediaStore.Audio.Media.SIZE to 12_345L,
    )

    @Before
    fun setUp() {
        FakeMediaProvider.queries = 0
        FakeMediaProvider.rows.clear()
        FakeMediaProvider.rows += listOf(
            row(1, "통화 녹음 김철수_251122_143022.m4a", 1_763_800_000, 120_000),
            row(2, "통화 녹음 01012345678_251121_090000.m4a", 1_763_700_000, 30_000),
            row(3, "이름없는파일.m4a", 1_763_000_000, 10_000),
        )
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun nothingIsReadWithoutPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        val repo = MediaStoreCallRecordingsRepository(app, scope)
        awaitUntil { repo.availability.value is RecordingsAvailability.PermissionRequired }
        assertEquals(0, FakeMediaProvider.queries)
        repo.onPermissionResult(false)
        awaitUntil { repo.availability.value is RecordingsAvailability.Denied }
        assertTrue(repo.recordings.value.isEmpty())
    }

    @Test
    fun callRecordingsAreReadAndParsed() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        val repo = MediaStoreCallRecordingsRepository(app, scope)
        awaitUntil { repo.recordings.value.size == 3 }
        assertEquals(RecordingsAvailability.Available, repo.availability.value)
        val byId = repo.recordings.value.associateBy { it.id }
        assertEquals("김철수", byId[1]?.label)
        assertEquals("01012345678", byId[2]?.labelDigits)
        // 파일 이름에 시각이 없으면 수정 시각 - 길이로 추정
        assertEquals(1_763_000_000_000L - 10_000, byId[3]?.startMillis)
        assertTrue(byId[1]!!.uri.startsWith("content://media/external/audio/media/1"))
        // 통화 녹음 폴더·파일 이름만 조회한다
        assertTrue(FakeMediaProvider.lastSelectionArgs!!.contains("%Recordings/Call%"))
    }
}
