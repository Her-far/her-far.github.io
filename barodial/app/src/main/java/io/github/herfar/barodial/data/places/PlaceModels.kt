package io.github.herfar.barodial.data.places

/**
 * 카카오 로컬 API(키워드로 장소 검색)의 장소 한 건.
 *
 * 카카오 운영 정책: 로컬 API 결과는 실시간 호출로만 쓰고, 장소 ID·URL 외에는 어떤 형태로든 저장하지 않는다.
 * 따라서 이 객체는 화면에 표시하는 동안 메모리에만 있고, 파일·DataStore·SavedStateHandle·검색기록에 쓰지 않는다.
 */
data class Place(
    val id: String,
    val name: String,
    /** 전화번호. 없으면 빈 문자열 */
    val phone: String,
    val roadAddress: String,
    /** 지번 주소 */
    val address: String,
    /** 예: "사회,공공기관 > 행정기관 > 시청" */
    val category: String,
    val categoryGroupName: String,
    /** 카카오맵 장소 상세 페이지 */
    val placeUrl: String,
    /** 기준 좌표를 보냈을 때만 있다 (미터) */
    val distanceMeters: Int?,
) {
    val hasPhone: Boolean get() = phone.any { it.isDigit() }

    /** 도로명 주소가 없으면 지번 주소 */
    val displayAddress: String get() = roadAddress.ifBlank { address }

    /** 업종: 카테고리의 마지막 단계 (예: "시청") */
    val shortCategory: String get() = category.substringAfterLast('>').trim().ifEmpty { categoryGroupName }
}

data class PlacePage(
    val places: List<Place>,
    val isEnd: Boolean,
    val totalCount: Int,
    val pageableCount: Int,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

/** 키워드 검색 요청. [near] 가 있으면 거리순(sort=distance)으로 요청한다. */
data class PlaceQuery(
    val query: String,
    val page: Int = 1,
    val size: Int = PAGE_SIZE,
    val near: GeoPoint? = null,
) {
    companion object {
        /** 카카오 키워드 검색의 한 페이지 최대 개수 */
        const val PAGE_SIZE = 15

        /** 카카오 키워드 검색의 최대 페이지 */
        const val MAX_PAGE = 45
    }
}

/**
 * 카카오 API 를 부르는 방법. API 키는 APK 에 넣지 않는다.
 *  - [Direct]: 사용자가 설정에서 입력한 자신의 REST API 키 (기기 안 앱 전용 저장소에만 보관)
 *  - [Proxy]: 키를 서버에 보관하는 경량 프록시 (저장소의 proxy/ 폴더). 앱은 프록시 주소와 선택적 접근 토큰만 안다.
 */
sealed interface PlaceApiConfig {
    data object None : PlaceApiConfig

    data class Direct(val restApiKey: String) : PlaceApiConfig {
        override fun toString(): String = "Direct(key=****)"
    }

    data class Proxy(val baseUrl: String, val token: String?) : PlaceApiConfig {
        override fun toString(): String = "Proxy($baseUrl)"
    }
}

/** 온라인 검색 실패 원인 (화면에 보여 줄 안내가 서로 다르다) */
sealed interface PlaceSearchError {
    /** 기기가 인터넷에 연결되어 있지 않다 */
    data object Offline : PlaceSearchError

    /** 서버에 닿지 못했거나 응답이 너무 늦다 */
    data object Network : PlaceSearchError

    /** 키(또는 프록시 토큰)가 틀렸다. HTTP 401 */
    data class InvalidCredentials(val viaProxy: Boolean, val detail: String?) : PlaceSearchError

    /** 권한 없음. 카카오맵 사용 설정(ON)이 꺼져 있는 경우 등. HTTP 403 */
    data class Forbidden(val detail: String?) : PlaceSearchError

    /** 호출 한도 초과. 카카오: HTTP 400 + 에러 코드 -10 ("API limit has been exceeded."), 또는 HTTP 429 */
    data object QuotaExceeded : PlaceSearchError

    /** 그 밖의 서버 오류 (5xx, 알 수 없는 4xx, 응답 형식 오류) */
    data class Server(val status: Int, val detail: String?) : PlaceSearchError
}

class PlaceSearchException(val error: PlaceSearchError, cause: Throwable? = null) : Exception(error.toString(), cause)

/** 설정값으로 API 연결 방식을 만든다. 값이 비었거나 형식이 틀리면 [PlaceApiConfig.None]. */
object PlaceApiConfigs {
    /** 카카오 REST API 키 형식 (보통 영문·숫자 32자). 키 체계가 바뀌어도 막히지 않게 넉넉히 받는다. */
    private val KEY_PATTERN = Regex("^[A-Za-z0-9_-]{16,128}$")

    fun isValidKey(key: String): Boolean = KEY_PATTERN.matches(key.trim())

    /** https 주소만 허용한다 (키·검색어가 평문으로 오가지 않게). */
    fun isValidProxyUrl(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("https://", ignoreCase = true)) return false
        return try {
            val uri = java.net.URI(u)
            !uri.host.isNullOrBlank() && uri.query == null && uri.fragment == null
        } catch (e: java.net.URISyntaxException) {
            false
        }
    }

    fun direct(key: String): PlaceApiConfig = if (isValidKey(key)) PlaceApiConfig.Direct(key.trim()) else PlaceApiConfig.None

    fun proxy(url: String, token: String): PlaceApiConfig =
        if (isValidProxyUrl(url)) PlaceApiConfig.Proxy(url.trim().trimEnd('/'), token.trim().ifEmpty { null }) else PlaceApiConfig.None
}
