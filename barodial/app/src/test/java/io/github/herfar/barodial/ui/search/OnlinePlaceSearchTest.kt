package io.github.herfar.barodial.ui.search

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.herfar.barodial.data.places.HttpResult
import io.github.herfar.barodial.data.places.KakaoLocalClient
import io.github.herfar.barodial.data.places.PlaceApiConfig
import io.github.herfar.barodial.data.places.PlaceSearchError
import io.github.herfar.barodial.testing.FakeLocationProvider
import io.github.herfar.barodial.testing.FakeNetworkMonitor
import io.github.herfar.barodial.testing.FakePlaceTransport
import io.github.herfar.barodial.testing.KakaoSamples
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 온라인 검색 흐름: 디바운스, 이전 요청 취소, 오류, 가까운 순, 더 보기 (가상 시간) */
@RunWith(AndroidJUnit4::class)
class OnlinePlaceSearchTest {

    private val key = "0123456789abcdef0123456789abcdef"
    private val enabled = OnlineSearchConfig(enabled = true, api = PlaceApiConfig.Direct(key), showOffHint = true)

    private val transport = FakePlaceTransport().apply {
        respond(
            mapOf(
                "전기공사협회" to KakaoSamples.ELECTRIC_ASSOCIATION,
                "한국전력공사" to KakaoSamples.KEPCO,
                "김포시청" to KakaoSamples.GIMPO_CITY_HALL,
                "약국" to KakaoSamples.PHARMACIES_NEARBY,
            ),
        )
    }
    private val query = MutableStateFlow("")
    private val config = MutableStateFlow(enabled)
    private val location = FakeLocationProvider()
    private val network = FakeNetworkMonitor()

    private fun TestScope.start(): OnlinePlaceSearch =
        OnlinePlaceSearch(backgroundScope, KakaoLocalClient(transport), location, network, query, config, debounceMillis = 350)

    private fun TestScope.type(text: String, thenWait: Long = 400) {
        query.value = text
        advanceTimeBy(thenWait)
        runCurrent()
    }

    private fun param(i: Int, name: String): String? = Uri.parse(transport.requests[i].first).getQueryParameter(name)

    @Test
    fun debounceSendsOnlyTheFinalQuery() = runTest {
        val s = start()
        for (t in listOf("김", "김포", "김포시", "김포시청")) type(t, thenWait = 100)
        assertTrue("입력 중에는 '검색 중'", s.state.value is OnlineSearchUi.Loading)
        assertEquals(emptyList<String>(), transport.queries())
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("김포시청"), transport.queries())
        val r = s.state.value as OnlineSearchUi.Results
        assertEquals("정확히 같은 이름이 먼저", "김포시청", r.items.first().place.name)
    }

    @Test
    fun changingQueryCancelsTheRequestInFlight() = runTest {
        var cancelled = 0
        val normal = transport.handler
        transport.handler = { url ->
            if (Uri.parse(url).getQueryParameter("query") == "한국전력") {
                try {
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            } else {
                normal(url)
            }
        }
        val s = start()
        type("한국전력")
        assertEquals(1, transport.requests.size)
        type("한국전력공사")
        assertEquals("이전 요청은 취소", 1, cancelled)
        assertEquals(listOf("한국전력", "한국전력공사"), transport.queries())
        assertEquals(4, (s.state.value as OnlineSearchUi.Results).items.size)
    }

    @Test
    fun shortNumericAndChoseongQueriesMakeNoRequest() = runTest {
        val s = start()
        for (t in listOf("김", "01012345678", "ㄱㅊㅅ", "1588-1234")) {
            type(t)
            assertEquals(OnlineSearchUi.Hidden, s.state.value)
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun sameNameBranchesAndMissingPhone() = runTest {
        val s = start()
        type("한국전력공사")
        val kepco = (s.state.value as OnlineSearchUi.Results).items
        assertEquals("전남 나주시", kepco.first { it.place.id == "k1" }.regionLabel)
        assertEquals("서울 중구", kepco.first { it.place.id == "k2" }.regionLabel)
        type("전기공사협회")
        val assoc = (s.state.value as OnlineSearchUi.Results).items
        assertEquals(4, assoc.size)
        assertTrue(assoc.first { it.place.id == "e4" }.place.phone.isEmpty())
    }

    @Test
    fun offlineShowsErrorWithoutRequestAndRetryWorksWhenBack() = runTest {
        network.online = false
        val s = start()
        type("김포시청")
        assertEquals(PlaceSearchError.Offline, (s.state.value as OnlineSearchUi.Failed).error)
        assertTrue(transport.requests.isEmpty())
        network.online = true
        s.retry()
        advanceTimeBy(400)
        runCurrent()
        assertTrue(s.state.value is OnlineSearchUi.Results)
    }

    @Test
    fun apiErrorsAndQuota() = runTest {
        val s = start()
        transport.handler = { HttpResult(400, KakaoSamples.ERROR_QUOTA) }
        type("김포시청")
        assertEquals(PlaceSearchError.QuotaExceeded, (s.state.value as OnlineSearchUi.Failed).error)
        transport.handler = { HttpResult(401, KakaoSamples.ERROR_BAD_KEY) }
        type("한국전력공사")
        assertTrue((s.state.value as OnlineSearchUi.Failed).error is PlaceSearchError.InvalidCredentials)
        transport.handler = { HttpResult(403, KakaoSamples.ERROR_DISABLED) }
        type("전기공사협회")
        assertTrue((s.state.value as OnlineSearchUi.Failed).error is PlaceSearchError.Forbidden)
    }

    @Test
    fun disabledOrUnconfiguredNeverCallsKakao() = runTest {
        config.value = enabled.copy(enabled = false)
        val s = start()
        type("김포시청")
        assertEquals(OnlineSearchUi.Off("김포시청"), s.state.value)
        config.value = enabled.copy(enabled = false, showOffHint = false)
        runCurrent()
        assertEquals(OnlineSearchUi.Hidden, s.state.value)
        config.value = enabled.copy(api = PlaceApiConfig.None)
        runCurrent()
        assertEquals(OnlineSearchUi.NotConfigured("김포시청"), s.state.value)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun nearbyWithoutPermissionSearchesNationwideThenUsesLocationAfterGrant() = runTest {
        val s = start()
        type("근처 약국")
        assertEquals("약국", param(0, "query"))
        assertNull("권한이 없으면 위치를 보내지 않는다", param(0, "x"))
        assertEquals(NearbyState.PERMISSION_NEEDED, (s.state.value as OnlineSearchUi.Results).nearby)

        location.granted = true
        s.onLocationPermissionChanged()
        advanceTimeBy(400)
        runCurrent()
        assertEquals("distance", param(1, "sort"))
        assertEquals("126.715600", param(1, "x"))
        val r = s.state.value as OnlineSearchUi.Results
        assertEquals(NearbyState.ON, r.nearby)
        assertEquals(listOf(120, 850, 1500), r.items.map { it.place.distanceMeters })
    }

    @Test
    fun nearbyToggleAndUnknownLocation() = runTest {
        location.granted = true
        location.point = null
        val s = start()
        s.setNearby(true)
        type("김포시청")
        assertNull(param(0, "x"))
        assertEquals(NearbyState.LOCATION_UNAVAILABLE, (s.state.value as OnlineSearchUi.Results).nearby)
        s.reset()
        runCurrent()
        assertEquals(false, s.nearbySelected.value)
    }

    @Test
    fun loadMoreAppendsNextPageWithoutDuplicates() = runTest {
        transport.handler = { url ->
            HttpResult(200, if (Uri.parse(url).getQueryParameter("page") == "2") KakaoSamples.KEPCO_PAGE2 else KakaoSamples.KEPCO)
        }
        val s = start()
        type("한국전력공사")
        val first = s.state.value as OnlineSearchUi.Results
        assertTrue(first.canLoadMore)
        s.loadMore()
        runCurrent()
        val more = s.state.value as OnlineSearchUi.Results
        assertEquals("2", param(1, "page"))
        assertEquals(5, more.items.size)
        assertEquals(false, more.canLoadMore)
    }

    @Test
    fun clearingTheQueryDiscardsResults() = runTest {
        val s = start()
        type("김포시청")
        assertTrue(s.state.value is OnlineSearchUi.Results)
        type("")
        assertEquals(OnlineSearchUi.Hidden, s.state.value)
    }
}
