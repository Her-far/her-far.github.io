package io.github.herfar.barodial.data.places

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP 응답 (본문은 UTF-8 문자열) */
data class HttpResult(val status: Int, val body: String)

/** 테스트에서 바꿔 끼울 수 있는 HTTP GET */
fun interface HttpTransport {
    /** 연결 실패·시간 초과는 IOException 으로 던진다. 코루틴이 취소되면 연결을 끊는다. */
    suspend fun get(url: String, headers: Map<String, String>): HttpResult
}

/**
 * HttpURLConnection 기반 GET (추가 라이브러리 없음).
 * 코루틴이 취소되면(검색어가 바뀌면) 연결을 끊어, 블로킹 중인 읽기도 즉시 끝나게 한다.
 * 응답은 디스크 캐시에 남기지 않는다 (useCaches = false).
 */
class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 8_000,
    private val maxBodyBytes: Int = 512 * 1024,
) : HttpTransport {

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        return suspendCancellableCoroutine { cont ->
            // 취소되면 연결을 끊어 블로킹 중인 읽기를 바로 끝낸다. 취소된 뒤의 결과·오류는 버려진다.
            cont.invokeOnCancellation { connection.disconnect() }
            Dispatchers.IO.asExecutor().execute {
                cont.resumeWith(runCatching { execute(connection, headers) })
            }
        }
    }

    private fun execute(connection: HttpURLConnection, headers: Map<String, String>): HttpResult {
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) connection.setRequestProperty(k, v)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val bytes = input.readNBytesCompat(maxBodyBytes)
                String(bytes, Charsets.UTF_8)
            }.orEmpty()
            return HttpResult(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

/**
 * 카카오 로컬 API "키워드로 장소 검색" 클라이언트.
 * 공식 문서: GET https://dapi.kakao.com/v2/local/search/keyword.json, 헤더 Authorization: KakaoAK ${REST_API_KEY}
 *
 *  - 결과는 호출한 쪽에 돌려줄 뿐 어디에도 저장하지 않는다 (카카오 운영 정책: 실시간 호출만 허용).
 *  - 보내는 정보는 검색어, 페이지, (사용자가 '가까운 순'을 켰을 때만) 대략적인 현재 좌표뿐이다.
 */
class KakaoLocalClient(private val transport: HttpTransport = UrlConnectionTransport()) {

    suspend fun searchKeyword(config: PlaceApiConfig, request: PlaceQuery): PlacePage {
        val (url, headers) = buildRequest(config, request)
        val viaProxy = config is PlaceApiConfig.Proxy
        val result = try {
            transport.get(url, headers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // 검색어가 바뀌어 취소된 요청이라면 오류로 바꾸지 않고 취소로 끝낸다.
            currentCoroutineContext().ensureActive()
            throw PlaceSearchException(PlaceSearchError.Network, e)
        }
        if (result.status in 200..299) {
            return try {
                parse(result.body)
            } catch (e: JSONException) {
                throw PlaceSearchException(PlaceSearchError.Server(result.status, "응답 형식 오류"), e)
            }
        }
        throw PlaceSearchException(classifyError(result, viaProxy))
    }

    /** 요청 URL 과 헤더. 프록시를 쓰면 카카오 키는 보내지 않는다 (프록시 서버가 붙인다). */
    fun buildRequest(config: PlaceApiConfig, request: PlaceQuery): Pair<String, Map<String, String>> {
        val base: String
        val headers = LinkedHashMap<String, String>()
        when (config) {
            PlaceApiConfig.None -> throw PlaceSearchException(PlaceSearchError.InvalidCredentials(viaProxy = false, detail = "설정 없음"))
            is PlaceApiConfig.Direct -> {
                base = KAKAO_BASE_URL
                headers["Authorization"] = "KakaoAK ${config.restApiKey.trim()}"
            }
            is PlaceApiConfig.Proxy -> {
                base = config.baseUrl.trim().trimEnd('/')
                config.token?.trim()?.takeIf { it.isNotEmpty() }?.let { headers[PROXY_TOKEN_HEADER] = it }
            }
        }
        val params = LinkedHashMap<String, String>()
        params["query"] = request.query
        params["page"] = request.page.coerceIn(1, PlaceQuery.MAX_PAGE).toString()
        params["size"] = request.size.coerceIn(1, PlaceQuery.PAGE_SIZE).toString()
        request.near?.let { p ->
            // 카카오 좌표: x = 경도, y = 위도. 거리순 정렬은 x, y 와 함께 써야 한다.
            params["x"] = "%.6f".format(java.util.Locale.ROOT, p.longitude)
            params["y"] = "%.6f".format(java.util.Locale.ROOT, p.latitude)
            params["sort"] = "distance"
        }
        val qs = params.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return "$base$KEYWORD_PATH?$qs" to headers
    }

    fun parse(body: String): PlacePage {
        val root = JSONObject(body)
        val meta = root.optJSONObject("meta")
        val docs = root.optJSONArray("documents")
        val places = ArrayList<Place>(docs?.length() ?: 0)
        if (docs != null) {
            for (i in 0 until docs.length()) {
                val d = docs.optJSONObject(i) ?: continue
                val name = d.optString("place_name").trim()
                if (name.isEmpty()) continue
                places += Place(
                    id = d.optString("id"),
                    name = name,
                    phone = d.optString("phone").trim(),
                    roadAddress = d.optString("road_address_name").trim(),
                    address = d.optString("address_name").trim(),
                    category = d.optString("category_name").trim(),
                    categoryGroupName = d.optString("category_group_name").trim(),
                    placeUrl = d.optString("place_url").trim(),
                    distanceMeters = d.optString("distance").toIntOrNull(),
                )
            }
        }
        return PlacePage(
            places = places,
            isEnd = meta?.optBoolean("is_end", true) ?: true,
            totalCount = meta?.optInt("total_count", places.size) ?: places.size,
            pageableCount = meta?.optInt("pageable_count", places.size) ?: places.size,
        )
    }

    /**
     * 카카오 오류 응답 분류.
     *  - 401 {"errorType":"AccessDeniedError","message":"cannot find Authorization : KakaoAK header"} 등 → 키 오류
     *  - 403 → 권한 없음 (카카오맵 사용 설정 OFF 등)
     *  - 400 + code -10 / "API limit has been exceeded." → 무료 제공량 소진 (카카오 공식 FAQ)
     *  - 429 → 요청 한도 초과
     */
    fun classifyError(result: HttpResult, viaProxy: Boolean): PlaceSearchError {
        val json = runCatching { JSONObject(result.body) }.getOrNull()
        val message = json?.let { it.optString("message").ifEmpty { it.optString("msg") } }?.ifEmpty { null }
        val errorType = json?.optString("errorType")?.ifEmpty { null }
        val code = json?.optInt("code", 0) ?: 0
        val limitExceeded = code == -10 ||
            message?.contains("limit has been exceeded", ignoreCase = true) == true ||
            errorType?.contains("Throttl", ignoreCase = true) == true ||
            errorType?.contains("Quota", ignoreCase = true) == true
        val detail = listOfNotNull(errorType, message).joinToString(": ").ifEmpty { null }
        return when {
            result.status == 429 || limitExceeded -> PlaceSearchError.QuotaExceeded
            result.status == 401 -> PlaceSearchError.InvalidCredentials(viaProxy, detail)
            result.status == 403 -> PlaceSearchError.Forbidden(detail)
            else -> PlaceSearchError.Server(result.status, detail)
        }
    }

    private fun encode(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    companion object {
        const val KAKAO_BASE_URL = "https://dapi.kakao.com"
        const val KEYWORD_PATH = "/v2/local/search/keyword.json"

        /** 프록시 접근 토큰 헤더 (proxy/worker.js 와 같아야 한다) */
        const val PROXY_TOKEN_HEADER = "X-BaroDial-Token"
    }
}
