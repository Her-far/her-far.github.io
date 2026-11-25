package io.github.herfar.barodial.dial

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers

/** 현재 기본 전화 앱 정보 (TelecomManager 기준, 패키지명을 추측하지 않는다). */
data class DefaultDialerInfo(
    val packageName: String?,
    val label: String?,
    /** 이 앱이 기본 전화 앱 역할을 가지고 있는지. 항상 false 여야 정상이다. */
    val thisAppHoldsDialerRole: Boolean,
)

/**
 * 발신·문자·연락처 편집을 시스템 앱에 위임하는 인텐트를 만든다.
 *
 * 핵심 원칙:
 *  - 기본은 ACTION_DIAL + tel: URI → 기본 전화 앱(삼성 전화)의 다이얼 화면이 번호와 함께 열린다.
 *    사용자가 거기서 통화 버튼을 누르면 실제 발신·통화 화면·통화 스크리닝은 모두 삼성 전화가 처리한다.
 *  - 원터치 발신(ACTION_CALL)은 사용자가 설정에서 켜고 CALL_PHONE 권한을 허용한 경우에만 쓴다.
 *    이때도 통화는 Telecom 프레임워크와 기본 전화 앱의 통화 화면에서 진행된다.
 *  - 삼성 전화 패키지명을 하드코딩하지 않는다.
 */
class DialerLauncher(context: Context) {

    private val appContext = context.applicationContext

    fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /** 원터치 발신을 실제로 쓸 수 있는지 (설정 + 권한 + 번호 종류). */
    fun canCallDirectly(number: String, directCallEnabled: Boolean): Boolean =
        directCallEnabled && hasCallPermission() && !KoreanPhoneNumbers.requiresDialerConfirmation(number)

    /**
     * 발신 인텐트. [direct] 가 false 면 ACTION_DIAL (권한 불필요).
     * Uri.fromParts 를 써서 '#' 같은 문자가 URI 조각으로 잘리지 않게 인코딩한다.
     */
    fun callIntent(number: String, direct: Boolean): Intent {
        val uri = Uri.fromParts("tel", number, null)
        return Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
    }

    fun smsIntent(number: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))

    fun lookupUri(contactId: Long, lookupKey: String): Uri =
        ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)

    fun viewContactIntent(contactId: Long, lookupKey: String): Intent =
        Intent(Intent.ACTION_VIEW, lookupUri(contactId, lookupKey))

    fun editContactIntent(contactId: Long, lookupKey: String): Intent =
        Intent(Intent.ACTION_EDIT, lookupUri(contactId, lookupKey))
            .putExtra("finishActivityOnSaveCompleted", true)

    /**
     * 시스템 연락처 앱의 "새 연락처" 등록 화면 (ACTION_INSERT + Contacts.CONTENT_TYPE).
     * [number] 가 있으면 전화번호 칸에 미리 넣는다. 저장은 연락처 앱이 하고, 이 앱은 DB 에 직접 쓰지 않는다.
     */
    fun insertContactIntent(number: String?): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            if (!number.isNullOrEmpty()) putExtra(ContactsContract.Intents.Insert.PHONE, number)
            putExtra(EXTRA_FINISH_ON_SAVE, true)
        }

    /**
     * 시스템 연락처 앱에서 기존 연락처를 골라 [number] 를 추가하는 화면 (ACTION_INSERT_OR_EDIT).
     * 고른 연락처의 편집 화면에 번호가 미리 들어간다.
     */
    fun insertOrEditContactIntent(number: String): Intent =
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            putExtra(EXTRA_FINISH_ON_SAVE, true)
        }

    /**
     * 기본 전화 앱의 통화기록 화면을 여는 인텐트.
     * Android 에는 "다른 앱의 특정 탭을 연다"는 공식 계약이 없으므로, 다음 순서로 시도한다.
     *  1) ACTION_VIEW + CallLog.Calls.CONTENT_TYPE (AOSP 다이얼러 등이 통화기록 화면으로 처리)
     *  2) ACTION_CALL_BUTTON (기본 전화 앱 열기)
     *  3) ACTION_DIAL (기본 전화 앱 다이얼 화면)
     */
    fun callLogIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE),
        Intent(Intent.ACTION_CALL_BUTTON),
        Intent(Intent.ACTION_DIAL),
    )

    /** 통화기록 화면을 직접 처리하는 앱이 있는지 (패키지 가시성은 매니페스트 queries 로 선언) */
    fun canOpenCallLogScreen(): Boolean = resolves(callLogIntents().first())

    /** 통화 녹음을 다른 앱(삼성 녹음기·음악 앱 등)으로 열기 */
    fun audioIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri.toUri(), "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun appDetailsSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", appContext.packageName, null))

    fun defaultAppsSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    fun defaultDialerInfo(): DefaultDialerInfo {
        val telecom = appContext.getSystemService<TelecomManager>()
        val pkg = try {
            telecom?.defaultDialerPackage
        } catch (e: SecurityException) {
            null
        }
        val label = pkg?.let { p ->
            try {
                val pm = appContext.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        val roleManager = appContext.getSystemService<RoleManager>()
        val holdsRole = roleManager?.let {
            it.isRoleAvailable(RoleManager.ROLE_DIALER) && it.isRoleHeld(RoleManager.ROLE_DIALER)
        } ?: false
        return DefaultDialerInfo(pkg, label, holdsRole)
    }

    fun resolves(intent: Intent): Boolean =
        appContext.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    /**
     * 액티비티 컨텍스트로 인텐트를 실행한다. 처리할 앱이 없거나 권한 문제가 있으면 false.
     * ACTION_CALL 이 SecurityException 으로 실패하면 ACTION_DIAL 로 대신 연다.
     */
    fun launch(context: Context, intent: Intent): Boolean {
        val target = if (context is android.app.Activity) intent else Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(target)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "처리할 앱이 없습니다: ${intent.action}")
            false
        } catch (e: SecurityException) {
            if (intent.action == Intent.ACTION_CALL) {
                launch(context, Intent(intent).setAction(Intent.ACTION_DIAL))
            } else {
                Log.w(TAG, "실행 권한이 없습니다: ${intent.action}", e)
                false
            }
        }
    }

    /** 여러 후보 중 처음으로 실행에 성공한 인텐트를 실행한다. */
    fun launchFirst(context: Context, intents: List<Intent>): Boolean = intents.any { launch(context, it) }

    companion object {
        private const val TAG = "DialerLauncher"

        /**
         * 연락처 편집 화면에 "저장하면 화면을 닫고 호출한 앱으로 돌아가라"고 알리는 값.
         * (Android 4.0+ 연락처 앱 공식 동작. 없으면 저장 후 연락처 보기 화면이 남는다)
         */
        const val EXTRA_FINISH_ON_SAVE = "finishActivityOnSaveCompleted"
    }
}
