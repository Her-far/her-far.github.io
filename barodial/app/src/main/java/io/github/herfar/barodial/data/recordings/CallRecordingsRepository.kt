package io.github.herfar.barodial.data.recordings

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RecordingsAvailability {
    /** 오디오 파일 읽기 권한을 아직 요청하지 않음 */
    data class PermissionRequired(val permission: String) : RecordingsAvailability

    data class Denied(val permission: String) : RecordingsAvailability

    data object Available : RecordingsAvailability
}

/**
 * 통화 녹음 파일 공급원.
 * 녹음은 삼성 전화가 하고, 이 앱은 저장된 파일을 읽어 보여 주고 재생만 한다.
 */
interface CallRecordingsRepository {
    val requiredPermission: String
    val availability: StateFlow<RecordingsAvailability>

    /** 최신순 */
    val recordings: StateFlow<List<CallRecording>>

    fun refresh()
    fun onPermissionResult(granted: Boolean)
}

/**
 * MediaStore 오디오 목록에서 통화 녹음 파일만 골라 읽는다.
 * 조건: 경로가 Recordings/Call 또는 Call 폴더이거나, 파일 이름이 "통화 녹음"/"Call recording" 으로 시작.
 * 음악 등 다른 오디오 파일은 조회 조건에서 제외한다.
 */
class MediaStoreCallRecordingsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val limit: Int = 2_000,
) : CallRecordingsRepository {

    override val requiredPermission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private var requestedBefore = false
    private val _availability = MutableStateFlow(currentAvailability())
    override val availability: StateFlow<RecordingsAvailability> = _availability.asStateFlow()

    private val _recordings = MutableStateFlow<List<CallRecording>>(emptyList())
    override val recordings: StateFlow<List<CallRecording>> = _recordings.asStateFlow()

    private var job: Job? = null
    private var observerRegistered = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = load(delayMillis = 1_000)
    }

    init {
        load(0)
    }

    private fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED

    private fun currentAvailability(): RecordingsAvailability = when {
        granted() -> RecordingsAvailability.Available
        requestedBefore -> RecordingsAvailability.Denied(requiredPermission)
        else -> RecordingsAvailability.PermissionRequired(requiredPermission)
    }

    override fun refresh() = load(0)

    override fun onPermissionResult(granted: Boolean) {
        requestedBefore = true
        load(0)
    }

    private fun load(delayMillis: Long) {
        job?.cancel()
        job = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val availability = currentAvailability()
            _availability.value = availability
            if (availability != RecordingsAvailability.Available) {
                _recordings.value = emptyList()
                return@launch
            }
            registerObserver()
            try {
                _recordings.value = withContext(Dispatchers.IO) { query() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _availability.value = RecordingsAvailability.Denied(requiredPermission)
                _recordings.value = emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "통화 녹음 목록을 읽지 못했습니다", e)
            }
        }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        try {
            context.contentResolver.registerContentObserver(collection(), true, observer)
            observerRegistered = true
        } catch (e: SecurityException) {
            Log.w(TAG, "녹음 변경 감지를 등록하지 못했습니다", e)
        }
    }

    private fun collection() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun query(): List<CallRecording> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%Recordings/Call%", "Call/%", "통화 녹음%", "Call recording%")
        val result = ArrayList<CallRecording>()
        val base = collection()
        context.contentResolver.query(base, projection, selection, args, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iModified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext() && result.size < limit) {
                val id = c.getLong(iId)
                val name = c.getString(iName).orEmpty()
                val duration = if (c.isNull(iDuration)) 0L else c.getLong(iDuration)
                val modifiedMillis = if (c.isNull(iModified)) 0L else c.getLong(iModified) * 1000
                val parsed = RecordingFileNames.parse(name)
                result += CallRecording(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id).toString(),
                    displayName = name,
                    label = parsed.label,
                    labelDigits = parsed.labelDigits,
                    startMillis = parsed.startMillis ?: (modifiedMillis - duration).coerceAtLeast(0),
                    durationMillis = duration,
                    sizeBytes = if (c.isNull(iSize)) 0L else c.getLong(iSize),
                )
            }
        }
        return result.sortedByDescending { it.startMillis }
    }

    private companion object {
        const val TAG = "CallRecordings"
    }
}
