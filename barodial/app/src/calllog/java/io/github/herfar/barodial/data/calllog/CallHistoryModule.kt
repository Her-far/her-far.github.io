package io.github.herfar.barodial.data.calllog

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/** calllog(실험) 빌드: CallLog Provider 를 읽는 구현을 사용한다. */
object CallHistoryModule {
    const val INCLUDED: Boolean = true

    fun create(context: Context, scope: CoroutineScope): CallHistoryRepository =
        AndroidCallHistoryRepository(context.applicationContext, scope)
}
