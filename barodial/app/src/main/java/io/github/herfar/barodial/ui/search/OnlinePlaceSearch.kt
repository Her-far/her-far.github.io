package io.github.herfar.barodial.ui.search

import io.github.herfar.barodial.data.places.GeoPoint
import io.github.herfar.barodial.data.places.KakaoLocalClient
import io.github.herfar.barodial.data.places.LocationProvider
import io.github.herfar.barodial.data.places.NetworkMonitor
import io.github.herfar.barodial.data.places.OnlineQuery
import io.github.herfar.barodial.data.places.Place
import io.github.herfar.barodial.data.places.PlaceApiConfig
import io.github.herfar.barodial.data.places.PlaceApiConfigs
import io.github.herfar.barodial.data.places.PlaceItem
import io.github.herfar.barodial.data.places.PlaceQuery
import io.github.herfar.barodial.data.places.PlaceRanking
import io.github.herfar.barodial.data.places.PlaceSearchError
import io.github.herfar.barodial.data.places.PlaceSearchException
import io.github.herfar.barodial.data.places.PlaceSearchRules
import io.github.herfar.barodial.data.settings.AppSettings
import io.github.herfar.barodial.data.settings.PlaceApiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 온라인 검색 설정 (AppSettings 에서 만든다) */
data class OnlineSearchConfig(
    val enabled: Boolean,
    val api: PlaceApiConfig,
    /** 기능이 꺼져 있을 때 검색 결과에 '켜는 방법' 안내를 보여 줄지 */
    val showOffHint: Boolean,
)

fun AppSettings.placeApiConfig(): PlaceApiConfig = when (placeApiMode) {
    PlaceApiMode.DIRECT -> PlaceApiConfigs.direct(kakaoRestApiKey)
    PlaceApiMode.PROXY -> PlaceApiConfigs.proxy(proxyUrl, proxyToken)
}

fun AppSettings.onlineSearchConfig(): OnlineSearchConfig =
    OnlineSearchConfig(enabled = onlineSearchEnabled, api = placeApiConfig(), showOffHint = !onlineSearchHintDismissed)

/** '가까운 순' 상태 */
enum class NearbyState {
    OFF,
    ON,

    /** 가까운 순을 원했지만 위치 권한이 없어 전국 결과를 보여 준다 */
    PERMISSION_NEEDED,

    /** 권한은 있지만 현재 위치를 알 수 없어 전국 결과를 보여 준다 */
    LOCATION_UNAVAILABLE,
}

/** 검색 결과 화면의 '온라인 검색' 영역 상태 */
sealed interface OnlineSearchUi {
    /** 검색어가 온라인 검색 조건에 맞지 않는다 (숫자·초성·짧은 검색어 등) → 영역 없음 */
    data object Hidden : OnlineSearchUi

    /** 기능이 꺼져 있다 → 켜는 방법 한 줄 안내 */
    data class Off(val keyword: String) : OnlineSearchUi

    /** 켰지만 API 키·프록시 설정이 없다 */
    data class NotConfigured(val keyword: String) : OnlineSearchUi

    data class Loading(val keyword: String, val nearby: NearbyState) : OnlineSearchUi

    data class Results(
        val keyword: String,
        val items: List<PlaceItem>,
        val canLoadMore: Boolean,
        val loadingMore: Boolean,
        val moreError: PlaceSearchError?,
        val nearby: NearbyState,
    ) : OnlineSearchUi

    data class Failed(val keyword: String, val error: PlaceSearchError, val nearby: NearbyState) : OnlineSearchUi
}

/**
 * 하단 검색창 입력에 맞춰 카카오 키워드 검색을 실행한다.
 *
 *  - 입력이 멈추고 [debounceMillis] 뒤에 요청한다. 검색어가 바뀌면 진행 중인 요청은 취소(연결 끊기)된다.
 *  - 짧은 검색어·번호·초성은 보내지 않는다 ([PlaceSearchRules]).
 *  - 결과는 이 객체의 메모리에만 있고, 검색어가 바뀌거나 검색을 닫으면 버린다 (카카오 정책: 저장 금지).
 *  - 온라인 검색이 실패해도 연락처 검색(별도 흐름)에는 영향이 없다.
 */
class OnlinePlaceSearch(
    private val scope: CoroutineScope,
    private val client: KakaoLocalClient,
    private val location: LocationProvider,
    private val network: NetworkMonitor,
    query: Flow<String>,
    config: Flow<OnlineSearchConfig>,
    private val debounceMillis: Long = PlaceSearchRules.DEBOUNCE_MILLIS,
) {
    private val _state = MutableStateFlow<OnlineSearchUi>(OnlineSearchUi.Hidden)
    val state: StateFlow<OnlineSearchUi> = _state.asStateFlow()

    private val nearbyToggle = MutableStateFlow(false)
    private val rerun = MutableStateFlow(0)

    /** 사용자가 '가까운 순'을 켰는지 */
    val nearbySelected: StateFlow<Boolean> = nearbyToggle.asStateFlow()

    /** 지금 화면에 보이는 검색 (더 보기용). 검색어가 바뀌면 버린다. */
    private class Session(
        val query: OnlineQuery,
        val api: PlaceApiConfig,
        val point: GeoPoint?,
        val nearby: NearbyState,
        var places: List<Place>,
        var nextPage: Int,
        var isEnd: Boolean,
    )

    private var session: Session? = null
    private var moreJob: Job? = null

    /** 실제로 보낸 요청 수 (테스트·진단용) */
    var requestCount = 0
        private set

    init {
        scope.launch {
            combine(
                query.map { PlaceSearchRules.parse(it) }.distinctUntilChanged(),
                config.distinctUntilChanged(),
                nearbyToggle,
                rerun,
            ) { q, c, near, _ -> Triple(q, c, near) }
                .collectLatest { (q, c, near) ->
                    moreJob?.cancel()
                    session = null
                    run(q, c, near)
                }
        }
    }

    private suspend fun run(q: OnlineQuery?, config: OnlineSearchConfig, toggle: Boolean) {
        if (q == null) {
            _state.value = OnlineSearchUi.Hidden
            return
        }
        if (!config.enabled) {
            _state.value = if (config.showOffHint) OnlineSearchUi.Off(q.keyword) else OnlineSearchUi.Hidden
            return
        }
        if (config.api == PlaceApiConfig.None) {
            _state.value = OnlineSearchUi.NotConfigured(q.keyword)
            return
        }
        val wantsNearby = q.wantsNearby || toggle
        _state.value = OnlineSearchUi.Loading(q.keyword, if (wantsNearby) NearbyState.ON else NearbyState.OFF)
        delay(debounceMillis)

        if (!network.isOnline()) {
            _state.value = OnlineSearchUi.Failed(q.keyword, PlaceSearchError.Offline, NearbyState.OFF)
            return
        }
        var point: GeoPoint? = null
        val nearby = when {
            !wantsNearby -> NearbyState.OFF
            !location.hasPermission() -> NearbyState.PERMISSION_NEEDED
            else -> {
                point = location.currentLocation()
                if (point != null) NearbyState.ON else NearbyState.LOCATION_UNAVAILABLE
            }
        }
        try {
            requestCount++
            val page = client.searchKeyword(config.api, PlaceQuery(q.keyword, page = 1, near = point))
            val s = Session(q, config.api, point, nearby, page.places, nextPage = 2, isEnd = page.isEnd)
            session = s
            _state.value = results(s, loadingMore = false, moreError = null)
        } catch (e: PlaceSearchException) {
            _state.value = OnlineSearchUi.Failed(q.keyword, e.error, nearby)
        }
    }

    private fun results(s: Session, loadingMore: Boolean, moreError: PlaceSearchError?) = OnlineSearchUi.Results(
        keyword = s.query.keyword,
        items = PlaceRanking.rank(s.query.keyword, s.places, byDistance = s.point != null),
        canLoadMore = !s.isEnd && s.nextPage <= MAX_PAGES,
        loadingMore = loadingMore,
        moreError = moreError,
        nearby = s.nearby,
    )

    /** 다음 페이지 (최대 [MAX_PAGES] 페이지 = 45건, 카카오 제공 한도) */
    fun loadMore() {
        val s = session ?: return
        val current = _state.value as? OnlineSearchUi.Results ?: return
        if (!current.canLoadMore || current.loadingMore) return
        _state.value = current.copy(loadingMore = true, moreError = null)
        moreJob = scope.launch {
            try {
                requestCount++
                val page = client.searchKeyword(s.api, PlaceQuery(s.query.keyword, page = s.nextPage, near = s.point))
                if (session !== s) return@launch
                s.places = PlaceRanking.merge(s.places, page.places)
                s.nextPage++
                s.isEnd = page.isEnd || page.places.isEmpty()
                _state.value = results(s, loadingMore = false, moreError = null)
            } catch (e: PlaceSearchException) {
                if (session === s) _state.value = results(s, loadingMore = false, moreError = e.error)
            }
        }
    }

    /** 같은 검색어로 다시 요청 (오류 후 [다시 시도]) */
    fun retry() {
        rerun.value++
    }

    fun setNearby(enabled: Boolean) {
        nearbyToggle.value = enabled
    }

    /** 위치 권한 허용/거부 후 다시 검색 */
    fun onLocationPermissionChanged() = retry()

    /** 검색을 닫을 때: 가까운 순 선택도 처음 상태로 */
    fun reset() {
        nearbyToggle.value = false
    }

    companion object {
        /** 15건 × 3페이지 = 45건 (카카오 pageable_count 최대값) */
        const val MAX_PAGES = 3
    }
}
