package io.github.herfar.barodial.data.calllog

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * standard(기본) 빌드: 통화기록을 읽지 않는다.
 * 삼성 전화를 기본 전화 앱으로 유지하는 동안 READ_CALL_LOG 를 안정적으로 얻을 수 없기 때문이다.
 */
object CallHistoryModule {
    const val INCLUDED: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, scope: CoroutineScope): CallHistoryRepository = UnavailableCallHistoryRepository
}
