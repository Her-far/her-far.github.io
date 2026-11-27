package io.github.herfar.barodial.data.places

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 카카오 서버(dapi.kakao.com)에 앱과 같은 코드로 요청해 보는 확인용 테스트.
 * 평소에는 건너뛴다. 실행: gradlew testDebugUnitTest -PkakaoLive=true --tests "*KakaoLiveApiTest*"
 *  - 환경변수 KAKAO_REST_API_KEY 가 없으면: 가짜 키로 요청해 401 이 '키 오류'로 분류되는지 확인
 *  - 있으면: 요청 예시 검색어로 실제 검색 (결과는 출력만 하고 저장하지 않는다)
 * 키는 저장소에 넣지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class KakaoLiveApiTest {

    @Test
    fun liveKeywordSearch() = runBlocking {
        assumeTrue("-PkakaoLive=true 일 때만 실행", System.getProperty("barodial.kakaoLive") == "true")
        val client = KakaoLocalClient(UrlConnectionTransport())
        val key = System.getenv("KAKAO_REST_API_KEY")?.trim().orEmpty()
        if (key.isEmpty()) {
            try {
                client.searchKeyword(PlaceApiConfig.Direct("00000000000000000000000000000000"), PlaceQuery("김포시청", size = 1))
                throw AssertionError("가짜 키로는 실패해야 함")
            } catch (e: PlaceSearchException) {
                println("LIVE 가짜 키 → ${e.error}")
                assertTrue(e.error is PlaceSearchError.InvalidCredentials)
            }
            return@runBlocking
        }
        for (q in listOf("전기공사협회", "한국전력공사", "김포시청")) {
            val page = client.searchKeyword(PlaceApiConfig.Direct(key), PlaceQuery(q))
            println("LIVE '$q' → ${page.totalCount}건, 전화 없는 결과 ${page.places.count { !it.hasPhone }}건")
            page.places.take(5).forEach { println("   ${it.name} | ${it.phone} | ${it.displayAddress} | ${it.shortCategory}") }
            assertTrue(page.places.isNotEmpty())
        }
    }
}
