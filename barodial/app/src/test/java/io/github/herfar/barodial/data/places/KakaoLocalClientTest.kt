package io.github.herfar.barodial.data.places

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import io.github.herfar.barodial.testing.FakePlaceTransport
import io.github.herfar.barodial.testing.KakaoSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class KakaoLocalClientTest {

    private val key = "0123456789abcdef0123456789abcdef"
    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
    }

    // ---------------------------------------------------------------- 요청

    @Test
    fun directRequestUsesOfficialEndpointAndKakaoAkHeader() {
        val (url, headers) = KakaoLocalClient().buildRequest(PlaceApiConfig.Direct(key), PlaceQuery("한국 전력공사"))
        val uri = Uri.parse(url)
        assertEquals("https", uri.scheme)
        assertEquals("dapi.kakao.com", uri.host)
        assertEquals("/v2/local/search/keyword.json", uri.path)
        assertEquals("한국 전력공사", uri.getQueryParameter("query"))
        assertTrue("공백은 %20 으로", url.contains("%20"))
        assertEquals("1", uri.getQueryParameter("page"))
        assertEquals("15", uri.getQueryParameter("size"))
        assertNull("위치를 보내지 않는다", uri.getQueryParameter("x"))
        assertNull(uri.getQueryParameter("sort"))
        assertEquals("KakaoAK $key", headers["Authorization"])
    }

    @Test
    fun nearbyRequestSendsLongitudeAsXAndSortsByDistance() {
        val (url, _) = KakaoLocalClient().buildRequest(PlaceApiConfig.Direct(key), PlaceQuery("약국", near = GeoPoint(37.615213, 126.715611)))
        val uri = Uri.parse(url)
        assertEquals("126.715611", uri.getQueryParameter("x"))
        assertEquals("37.615213", uri.getQueryParameter("y"))
        assertEquals("distance", uri.getQueryParameter("sort"))
    }

    @Test
    fun proxyRequestNeverSendsKakaoKey() {
        val (url, headers) = KakaoLocalClient().buildRequest(PlaceApiConfig.Proxy("https://proxy.example.com/", "tok"), PlaceQuery("김포시청", page = 2))
        assertTrue(url.startsWith("https://proxy.example.com/v2/local/search/keyword.json?"))
        assertEquals("2", Uri.parse(url).getQueryParameter("page"))
        assertEquals("tok", headers[KakaoLocalClient.PROXY_TOKEN_HEADER])
        assertFalse(headers.containsKey("Authorization"))
    }

    @Test
    fun pageAndSizeAreClampedToKakaoLimits() {
        val (url, _) = KakaoLocalClient().buildRequest(PlaceApiConfig.Direct(key), PlaceQuery("a", page = 99, size = 99))
        assertEquals("45", Uri.parse(url).getQueryParameter("page"))
        assertEquals("15", Uri.parse(url).getQueryParameter("size"))
    }

    @Test
    fun configValidation() {
        assertEquals(PlaceApiConfig.None, PlaceApiConfigs.direct(""))
        assertEquals(PlaceApiConfig.None, PlaceApiConfigs.direct("짧음"))
        assertEquals(PlaceApiConfig.Direct(key), PlaceApiConfigs.direct(" $key "))
        assertEquals("http 는 거부", PlaceApiConfig.None, PlaceApiConfigs.proxy("http://proxy.example.com", ""))
        assertEquals(PlaceApiConfig.None, PlaceApiConfigs.proxy("https://", ""))
        assertEquals(PlaceApiConfig.Proxy("https://proxy.example.com", null), PlaceApiConfigs.proxy("https://proxy.example.com/", " "))
        assertFalse("키가 문자열로 찍히지 않는다", PlaceApiConfig.Direct(key).toString().contains(key))
    }

    // ---------------------------------------------------------------- 응답 해석

    @Test
    fun parsesOfficialResponseExample() {
        // 카카오 공식 문서의 응답 예시 그대로
        val body = """
            {"meta":{"same_name":{"region":[],"keyword":"카카오프렌즈","selected_region":""},"pageable_count":14,"total_count":14,"is_end":true},
             "documents":[{"place_name":"카카오프렌즈 코엑스점","distance":"418","place_url":"http://place.map.kakao.com/26338954",
             "category_name":"가정,생활 > 문구,사무용품 > 디자인문구 > 카카오프렌즈","address_name":"서울 강남구 삼성동 159",
             "road_address_name":"서울 강남구 영동대로 513","id":"26338954","phone":"02-6002-1880","category_group_code":"",
             "category_group_name":"","x":"127.05902969025047","y":"37.51207412593136"}]}
        """.trimIndent()
        val page = KakaoLocalClient().parse(body)
        assertTrue(page.isEnd)
        assertEquals(14, page.totalCount)
        val p = page.places.single()
        assertEquals("26338954", p.id)
        assertEquals("카카오프렌즈 코엑스점", p.name)
        assertEquals("02-6002-1880", p.phone)
        assertEquals("서울 강남구 영동대로 513", p.displayAddress)
        assertEquals("카카오프렌즈", p.shortCategory)
        assertEquals(418, p.distanceMeters)
        assertEquals("http://place.map.kakao.com/26338954", p.placeUrl)
    }

    @Test
    fun placeWithoutPhoneOrRoadAddress() {
        val page = KakaoLocalClient().parse(KakaoSamples.ELECTRIC_ASSOCIATION)
        val noPhone = page.places.first { it.id == "e4" }
        assertFalse(noPhone.hasPhone)
        val kepco = KakaoLocalClient().parse(KakaoSamples.KEPCO).places.first { it.id == "k4" }
        assertEquals("도로명이 없으면 지번 주소", "서울 중구 예시동 8", kepco.displayAddress)
        assertNull(kepco.distanceMeters)
    }

    // ---------------------------------------------------------------- 오류 분류

    @Test
    fun classifiesRealAndDocumentedErrors() {
        val c = KakaoLocalClient()
        assertEquals(
            PlaceSearchError.InvalidCredentials(false, "AccessDeniedError: cannot find Authorization : KakaoAK header"),
            c.classifyError(HttpResult(401, KakaoSamples.ERROR_NO_KEY), viaProxy = false),
        )
        assertTrue(c.classifyError(HttpResult(401, KakaoSamples.ERROR_BAD_KEY), viaProxy = false) is PlaceSearchError.InvalidCredentials)
        assertEquals(PlaceSearchError.QuotaExceeded, c.classifyError(HttpResult(400, KakaoSamples.ERROR_QUOTA), viaProxy = false))
        assertEquals(PlaceSearchError.QuotaExceeded, c.classifyError(HttpResult(429, ""), viaProxy = false))
        assertTrue(c.classifyError(HttpResult(403, KakaoSamples.ERROR_DISABLED), viaProxy = false) is PlaceSearchError.Forbidden)
        assertEquals(PlaceSearchError.Server(500, null), c.classifyError(HttpResult(500, "<html>"), viaProxy = false))
        assertEquals(
            PlaceSearchError.InvalidCredentials(true, "ProxyTokenError: invalid proxy token"),
            c.classifyError(HttpResult(401, """{"errorType":"ProxyTokenError","message":"invalid proxy token"}"""), viaProxy = true),
        )
    }

    @Test
    fun transportFailureBecomesNetworkErrorAndBadJsonBecomesServerError() = runBlocking {
        val failing = KakaoLocalClient { _, _ -> throw java.net.UnknownHostException("dapi.kakao.com") }
        assertEquals(PlaceSearchError.Network, errorOf { failing.searchKeyword(PlaceApiConfig.Direct(key), PlaceQuery("김포시청")) })
        val garbage = FakePlaceTransport().apply { handler = { HttpResult(200, "not json") } }
        assertTrue(errorOf { KakaoLocalClient(garbage).searchKeyword(PlaceApiConfig.Direct(key), PlaceQuery("김포시청")) } is PlaceSearchError.Server)
    }

    // ---------------------------------------------------------------- 실제 HTTP (로컬 서버)

    private fun startServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            try {
                handler(ex)
            } catch (_: Exception) {
            } finally {
                ex.close()
            }
        }
        s.executor = java.util.concurrent.Executors.newCachedThreadPool()
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    private fun reply(ex: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json;charset=UTF-8")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    @Test
    fun realHttpSuccessAndErrorBodies() = runBlocking {
        var seenAuth: String? = null
        var seenQuery: String? = null
        val base = startServer { ex ->
            seenAuth = ex.requestHeaders.getFirst(KakaoLocalClient.PROXY_TOKEN_HEADER)
            seenQuery = Uri.parse("http://x" + ex.requestURI.toString()).getQueryParameter("query")
            when (seenQuery) {
                "한국전력공사" -> reply(ex, 200, KakaoSamples.KEPCO)
                "한도" -> reply(ex, 400, KakaoSamples.ERROR_QUOTA)
                else -> reply(ex, 401, KakaoSamples.ERROR_BAD_KEY)
            }
        }
        val client = KakaoLocalClient(UrlConnectionTransport())
        val config = PlaceApiConfig.Proxy(base, "tok")
        val page = client.searchKeyword(config, PlaceQuery("한국전력공사"))
        assertEquals("한국전력공사", seenQuery)
        assertEquals("tok", seenAuth)
        assertEquals(4, page.places.size)
        assertFalse(page.isEnd)
        assertEquals(PlaceSearchError.QuotaExceeded, errorOf { client.searchKeyword(config, PlaceQuery("한도")) })
        assertTrue(errorOf { client.searchKeyword(config, PlaceQuery("키오류")) } is PlaceSearchError.InvalidCredentials)
    }

    @Test
    fun connectionRefusedIsNetworkError() = runBlocking {
        val port = ServerSocket(0).use { it.localPort } // 닫힌 포트
        val client = KakaoLocalClient(UrlConnectionTransport(connectTimeoutMillis = 2_000, readTimeoutMillis = 2_000))
        assertEquals(PlaceSearchError.Network, errorOf { client.searchKeyword(PlaceApiConfig.Proxy("http://127.0.0.1:$port", null), PlaceQuery("김포시청")) })
    }

    @Test
    fun cancellingAQueryDisconnectsTheSlowRequest() = runBlocking {
        val requestArrived = CountDownLatch(1)
        val base = startServer { ex ->
            requestArrived.countDown()
            Thread.sleep(10_000) // 응답이 매우 느린 서버
            reply(ex, 200, KakaoSamples.KEPCO)
        }
        val client = KakaoLocalClient(UrlConnectionTransport(readTimeoutMillis = 30_000))
        val started = System.currentTimeMillis()
        val call = async(Dispatchers.Default) { client.searchKeyword(PlaceApiConfig.Proxy(base, null), PlaceQuery("느린검색")) }
        assertTrue(requestArrived.await(5, TimeUnit.SECONDS))
        delay(100)
        call.cancel() // 검색어가 바뀐 상황
        withTimeout(3_000) { call.join() }
        assertTrue("취소하면 느린 응답을 기다리지 않는다", System.currentTimeMillis() - started < 5_000)
        assertTrue(call.isCancelled)
    }

    private suspend fun errorOf(block: suspend () -> Unit): PlaceSearchError {
        try {
            block()
        } catch (e: PlaceSearchException) {
            return e.error
        }
        fail("PlaceSearchException 이 나야 함")
        throw IllegalStateException()
    }
}
