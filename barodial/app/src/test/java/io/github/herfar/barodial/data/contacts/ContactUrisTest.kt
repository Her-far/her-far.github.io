package io.github.herfar.barodial.data.contacts

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactUrisTest {

    @Test
    fun lookupUriWithId() {
        assertEquals(ContactRef("0r12-ABC", 42), ContactUris.parse(Uri.parse("content://com.android.contacts/contacts/lookup/0r12-ABC/42")))
    }

    @Test
    fun lookupUriWithoutId() {
        assertEquals(ContactRef("0r12-ABC", null), ContactUris.parse(Uri.parse("content://com.android.contacts/contacts/lookup/0r12-ABC")))
    }

    @Test
    fun contactIdUri() {
        assertEquals(ContactRef(null, 7), ContactUris.parse(Uri.parse("content://com.android.contacts/contacts/7")))
    }

    @Test
    fun otherUrisAreIgnored() {
        assertNull(ContactUris.parse(null))
        assertNull(ContactUris.parse(Uri.parse("content://com.android.contacts/raw_contacts/7")))
        assertNull(ContactUris.parse(Uri.parse("content://media/external/audio/media/7")))
        assertNull(ContactUris.parse(Uri.parse("content://com.android.contacts/contacts/abc")))
    }
}
