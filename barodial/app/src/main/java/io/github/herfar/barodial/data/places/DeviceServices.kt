package io.github.herfar.barodial.data.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** '가까운 순' 정렬에 쓰는 현재 위치. 검색할 때만 한 번 읽고 저장하지 않는다. */
interface LocationProvider {
    /** 대략적인 위치 권한 (ACCESS_COARSE_LOCATION) */
    val permission: String
    fun hasPermission(): Boolean

    /** 권한이 없거나 위치를 알 수 없으면 null */
    suspend fun currentLocation(): GeoPoint?
}

/** 위치를 쓰지 않는 기본값 (테스트·위치 미지원 기기) */
object NoLocationProvider : LocationProvider {
    override val permission: String = Manifest.permission.ACCESS_COARSE_LOCATION
    override fun hasPermission(): Boolean = false
    override suspend fun currentLocation(): GeoPoint? = null
}

/** 인터넷 연결 여부 (연결이 없으면 요청을 보내지 않고 바로 안내한다) */
fun interface NetworkMonitor {
    fun isOnline(): Boolean
}

/**
 * 플랫폼 LocationManager 만 쓴다 (Google Play 서비스 의존 없음).
 * 최근 10분 안의 마지막 위치가 있으면 그것을, 없으면 Android 11+ 에서 현재 위치를 한 번 요청한다(최대 6초).
 */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    override val permission: String = Manifest.permission.ACCESS_COARSE_LOCATION

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val lm = appContext.getSystemService<LocationManager>() ?: return null
        val providers = try {
            lm.getProviders(true)
        } catch (e: SecurityException) {
            return null
        }
        val recent = providers.mapNotNull { p ->
            try {
                lm.getLastKnownLocation(p)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }
        if (recent != null && System.currentTimeMillis() - recent.time < FRESH_MILLIS) return recent.toGeoPoint()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            if (provider != null) {
                val fresh = withTimeoutOrNull(CURRENT_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            lm.getCurrentLocation(provider, signal, appContext.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                        } catch (e: SecurityException) {
                            if (cont.isActive) cont.resume(null)
                        } catch (e: IllegalArgumentException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return fresh.toGeoPoint()
            }
        }
        return recent?.toGeoPoint()
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val FRESH_MILLIS = 10 * 60_000L
        const val CURRENT_TIMEOUT_MILLIS = 6_000L
    }
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val cm = context.applicationContext.getSystemService<ConnectivityManager>()

    override fun isOnline(): Boolean {
        val manager = cm ?: return true
        return try {
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: SecurityException) {
            true
        }
    }
}
