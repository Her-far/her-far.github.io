package io.github.herfar.barodial.data.contacts

import android.net.Uri
import android.provider.ContactsContract

/** 연락처 앱이 돌려준 URI 가 가리키는 연락처 (lookup key 또는 contact id) */
data class ContactRef(val lookupKey: String?, val contactId: Long?)

/**
 * 시스템 연락처 앱이 저장 결과로 돌려주는 URI 를 해석한다.
 *  - content://com.android.contacts/contacts/lookup/{lookupKey}/{id}
 *  - content://com.android.contacts/contacts/lookup/{lookupKey}
 *  - content://com.android.contacts/contacts/{id}
 * 그 밖의 형식(raw_contacts 등)은 null 을 돌려주고, 호출한 쪽은 전화번호로 연락처를 찾는다.
 */
object ContactUris {
    fun parse(uri: Uri?): ContactRef? {
        if (uri == null || uri.authority != ContactsContract.AUTHORITY) return null
        val s = uri.pathSegments
        if (s.isEmpty() || s[0] != "contacts") return null
        return when {
            s.size >= 3 && s[1] == "lookup" -> ContactRef(s[2], s.getOrNull(3)?.toLongOrNull())
            s.size == 2 -> s[1].toLongOrNull()?.let { ContactRef(null, it) }
            else -> null
        }
    }
}
