package io.github.herfar.barodial.data.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.search.ContactSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Contacts Provider 에서 전화번호가 있는 연락처를 읽어 메모리에 색인한다.
 * 연락처가 바뀌면 ContentObserver 로 감지해 다시 읽는다.
 */
class AndroidContactsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: ContactSearchEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ContactsRepository {

    private val _state = MutableStateFlow<ContactsState>(ContactsState.Loading)
    override val state: StateFlow<ContactsState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            schedule(delayMillis = 500)
        }
    }

    init {
        schedule(0)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    override fun refreshIfNeeded() {
        val granted = hasPermission()
        val current = _state.value
        val needsReload = when (current) {
            is ContactsState.Ready, ContactsState.Loading -> !granted
            ContactsState.PermissionRequired, is ContactsState.Error -> granted
        }
        if (needsReload) schedule(0)
    }

    override fun reload() = schedule(0)

    private fun schedule(delayMillis: Long) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            if (!hasPermission()) {
                _state.value = ContactsState.PermissionRequired
                return@launch
            }
            registerObserver()
            try {
                val raw = withContext(ioDispatcher) { ContactsLoader.load(context.contentResolver) }
                _state.value = withContext(computeDispatcher) {
                    ContactsState.Ready.build(ContactsState.Ready.sortContacts(raw), engine)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _state.value = ContactsState.PermissionRequired
            } catch (e: Exception) {
                Log.w(TAG, "연락처를 읽지 못했습니다", e)
                if (_state.value !is ContactsState.Ready) {
                    _state.value = ContactsState.Error(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        try {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observerRegistered = true
        } catch (e: SecurityException) {
            Log.w(TAG, "연락처 변경 감지를 등록하지 못했습니다", e)
        }
    }

    private companion object {
        const val TAG = "ContactsRepository"
    }
}

/** Data 테이블 한 번의 질의로 이름·번호·회사·별명을 모두 읽는다. */
internal object ContactsLoader {

    val PROJECTION = arrayOf(
        Data._ID,
        Data.CONTACT_ID,
        Data.LOOKUP_KEY,
        Data.DISPLAY_NAME_PRIMARY,
        Data.PHOTO_THUMBNAIL_URI,
        Data.PHOTO_URI,
        Data.STARRED,
        Data.MIMETYPE,
        Data.DATA1,
        Data.DATA2,
        Data.DATA3,
        Data.DATA4,
        Data.IS_SUPER_PRIMARY,
    )

    private const val SELECTION = "${Data.MIMETYPE} IN (?,?,?)"
    private val SELECTION_ARGS = arrayOf(
        Phone.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE,
        Nickname.CONTENT_ITEM_TYPE,
    )

    private class Builder(val id: Long, val lookupKey: String) {
        var name: String? = null
        var thumb: String? = null
        var photo: String? = null
        var starred = false
        val phones = LinkedHashMap<String, PhoneNumber>()
        var organization: String? = null
        var jobTitle: String? = null
        val nicknames = LinkedHashSet<String>()
    }

    fun load(resolver: ContentResolver): List<Contact> {
        val builders = LinkedHashMap<Long, Builder>()
        resolver.query(Data.CONTENT_URI, PROJECTION, SELECTION, SELECTION_ARGS, null)?.use { c ->
            val iContact = c.getColumnIndexOrThrow(Data.CONTACT_ID)
            val iLookup = c.getColumnIndexOrThrow(Data.LOOKUP_KEY)
            val iName = c.getColumnIndexOrThrow(Data.DISPLAY_NAME_PRIMARY)
            val iThumb = c.getColumnIndexOrThrow(Data.PHOTO_THUMBNAIL_URI)
            val iPhoto = c.getColumnIndexOrThrow(Data.PHOTO_URI)
            val iStarred = c.getColumnIndexOrThrow(Data.STARRED)
            val iMime = c.getColumnIndexOrThrow(Data.MIMETYPE)
            val iData1 = c.getColumnIndexOrThrow(Data.DATA1)
            val iData2 = c.getColumnIndexOrThrow(Data.DATA2)
            val iData3 = c.getColumnIndexOrThrow(Data.DATA3)
            val iData4 = c.getColumnIndexOrThrow(Data.DATA4)
            val iSuper = c.getColumnIndexOrThrow(Data.IS_SUPER_PRIMARY)

            while (c.moveToNext()) {
                val contactId = c.getLong(iContact)
                val lookup = c.getString(iLookup) ?: contactId.toString()
                val b = builders.getOrPut(contactId) { Builder(contactId, lookup) }
                if (b.name.isNullOrBlank()) b.name = c.getString(iName)
                if (b.thumb == null) b.thumb = c.getString(iThumb)
                if (b.photo == null) b.photo = c.getString(iPhoto)
                if (!c.isNull(iStarred) && c.getInt(iStarred) != 0) b.starred = true

                when (c.getString(iMime)) {
                    Phone.CONTENT_ITEM_TYPE -> {
                        val number = c.getString(iData1)?.trim().orEmpty()
                        if (number.isEmpty()) continue
                        val national = KoreanPhoneNumbers.toNationalDigits(number)
                        // 같은 번호가 여러 계정에 중복 저장된 경우 하나만 남긴다.
                        val key = national.ifEmpty { number }
                        val isDefault = !c.isNull(iSuper) && c.getInt(iSuper) != 0
                        val existing = b.phones[key]
                        if (existing == null || (isDefault && !existing.isDefault)) {
                            b.phones[key] = PhoneNumber(
                                number = number,
                                nationalDigits = national,
                                type = if (c.isNull(iData2)) Phone.TYPE_OTHER else c.getInt(iData2),
                                label = c.getString(iData3),
                                isDefault = isDefault || existing?.isDefault == true,
                            )
                        }
                    }
                    Organization.CONTENT_ITEM_TYPE -> {
                        val company = c.getString(iData1)?.trim()
                        val title = c.getString(iData4)?.trim()
                        if (b.organization.isNullOrEmpty() && !company.isNullOrEmpty()) b.organization = company
                        if (b.jobTitle.isNullOrEmpty() && !title.isNullOrEmpty()) b.jobTitle = title
                    }
                    Nickname.CONTENT_ITEM_TYPE -> {
                        c.getString(iData1)?.trim()?.takeIf { it.isNotEmpty() }?.let { b.nicknames += it }
                    }
                }
            }
        }
        return builders.values
            .filter { it.phones.isNotEmpty() }
            .map { b ->
                val firstNumber = b.phones.values.first().number
                Contact(
                    id = b.id,
                    lookupKey = b.lookupKey,
                    displayName = b.name?.takeIf { it.isNotBlank() } ?: KoreanPhoneNumbers.format(firstNumber),
                    photoThumbnailUri = b.thumb,
                    photoUri = b.photo,
                    starred = b.starred,
                    phones = b.phones.values.toList(),
                    organization = b.organization,
                    jobTitle = b.jobTitle,
                    nicknames = b.nicknames.toList(),
                )
            }
    }
}
