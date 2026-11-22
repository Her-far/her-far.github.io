package io.github.herfar.barodial.dial

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DialerLauncherTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val launcher = DialerLauncher(app)

    @Test
    fun dialIntentUsesTelUriAndActionDial() {
        val i = launcher.callIntent("010-1234-5678", direct = false)
        assertEquals(Intent.ACTION_DIAL, i.action)
        assertEquals("tel:010-1234-5678", i.data.toString())
    }

    @Test
    fun hashCharactersAreEncoded() {
        // '#' 이 URI 조각(fragment)으로 잘리면 *#06# 같은 코드가 깨진다.
        val i = launcher.callIntent("*#06#", direct = false)
        assertEquals("*#06#", i.data?.schemeSpecificPart)
        assertTrue(i.data.toString().contains("%23"))
    }

    @Test
    fun directCallOnlyWithPermissionAndForOrdinaryNumbers() {
        shadowOf(app).denyPermissions(Manifest.permission.CALL_PHONE)
        assertFalse(launcher.canCallDirectly("01012345678", directCallEnabled = true))
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        assertTrue(launcher.canCallDirectly("01012345678", directCallEnabled = true))
        assertFalse(launcher.canCallDirectly("01012345678", directCallEnabled = false))
        assertFalse(launcher.canCallDirectly("112", directCallEnabled = true))
        assertFalse(launcher.canCallDirectly("*#06#", directCallEnabled = true))
        assertEquals(Intent.ACTION_CALL, launcher.callIntent("01012345678", direct = true).action)
    }

    @Test
    fun thisAppIsNotADialIntentHandler() {
        // 우리 앱이 DIAL / tel: 을 받지 않아야 발신 요청이 항상 기본 전화 앱(삼성 전화)으로만 간다.
        val pm = app.packageManager
        val dial = pm.queryIntentActivities(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0101234")), 0)
        val view = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("tel:0101234")), 0)
        val call = pm.queryIntentActivities(Intent(Intent.ACTION_CALL, Uri.parse("tel:0101234")), 0)
        assertTrue((dial + view + call).none { it.activityInfo.packageName == app.packageName })
    }

    @Test
    fun defaultDialerIsReadFromTelecomWithoutHardcoding() {
        val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        shadowOf(telecom).setDefaultDialerPackage("com.example.vendor.phone")
        val info = launcher.defaultDialerInfo()
        assertEquals("com.example.vendor.phone", info.packageName)
        assertFalse(info.thisAppHoldsDialerRole)
    }

    @Test
    fun launchFromActivityStartsIntent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val intent = launcher.callIntent("01012345678", direct = false)
        assertTrue(launcher.launch(activity, intent))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertEquals("tel:01012345678", started.data.toString())
    }

    @Test
    fun callLogIntentChainStartsWithSystemCallLogScreen() {
        val intents = launcher.callLogIntents()
        assertEquals(Intent.ACTION_VIEW, intents[0].action)
        assertEquals(CallLog.Calls.CONTENT_TYPE, intents[0].type)
        assertEquals(Intent.ACTION_CALL_BUTTON, intents[1].action)
        assertEquals(Intent.ACTION_DIAL, intents[2].action)
    }

    @Test
    fun contactIntentsDelegateToSystemContactsApp() {
        assertEquals(Intent.ACTION_EDIT, launcher.editContactIntent(5, "lk5").action)
        assertTrue(launcher.viewContactIntent(5, "lk5").data.toString().startsWith("content://com.android.contacts/contacts/lookup/lk5"))
        val insert = launcher.insertContactIntent("01012345678")
        assertEquals(Intent.ACTION_INSERT, insert.action)
        assertEquals("01012345678", insert.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE))
        assertEquals("smsto:01012345678", launcher.smsIntent("01012345678").data.toString())
    }
}
