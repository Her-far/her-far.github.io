package io.github.herfar.barodial.testing

import org.json.JSONArray
import org.json.JSONObject

/**
 * 테스트 전용 가짜 카카오 키워드 검색 응답.
 * 형식은 카카오 공식 문서의 응답 예시와 같고, 상호는 사용자 요청의 예시를 쓰되
 * 전화번호·주소는 실제 정보가 아닌 예시 값(…-0000-…, '예시로')이다. 앱 본체에는 들어가지 않는다.
 */
object KakaoSamples {

    data class Doc(
        val id: String,
        val name: String,
        val phone: String,
        val road: String,
        val address: String = road,
        val category: String = "",
        val group: String = "",
        val distance: Int? = null,
    )

    fun page(vararg docs: Doc, isEnd: Boolean = true, total: Int = docs.size): String {
        val arr = JSONArray()
        for (d in docs) {
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("place_name", d.name)
                    .put("phone", d.phone)
                    .put("road_address_name", d.road)
                    .put("address_name", d.address)
                    .put("category_name", d.category)
                    .put("category_group_code", "")
                    .put("category_group_name", d.group)
                    .put("place_url", "http://place.map.kakao.com/${d.id}")
                    .put("x", "126.7")
                    .put("y", "37.6")
                    .put("distance", d.distance?.toString() ?: ""),
            )
        }
        return JSONObject()
            .put("meta", JSONObject().put("total_count", total).put("pageable_count", minOf(total, 45)).put("is_end", isEnd)
                .put("same_name", JSONObject().put("region", JSONArray()).put("keyword", "").put("selected_region", "")))
            .put("documents", arr)
            .toString()
    }

    private const val ASSOCIATION = "사회,공공기관 > 단체,협회 > 협회"
    private const val PUBLIC_CORP = "사회,공공기관 > 공사,공단"

    val ELECTRIC_ASSOCIATION = page(
        Doc("e1", "한국전기공사협회", "02-0000-1001", "서울 강서구 예시로 1", category = ASSOCIATION),
        Doc("e2", "한국전기공사협회 경기도회", "031-000-1002", "경기 수원시 예시로 2", category = ASSOCIATION),
        Doc("e3", "한국전기공사협회 서울특별시회", "02-0000-1003", "서울 영등포구 예시로 3", category = ASSOCIATION),
        // 전화번호가 없는 결과 → 전화 버튼 비활성
        Doc("e4", "한국전기공사협회 부산광역시회", "", "부산 연제구 예시로 4", category = ASSOCIATION),
    )

    /** 같은 이름(한국전력공사)이 두 곳 → 지역으로 구분 */
    val KEPCO = page(
        Doc("k3", "한국전력공사 김포지사", "031-000-2003", "경기 김포시 예시로 7", category = PUBLIC_CORP),
        Doc("k1", "한국전력공사", "061-000-2001", "전남 나주시 예시로 5", category = PUBLIC_CORP),
        Doc("k2", "한국전력공사", "02-0000-2002", "서울 중구 예시로 6", category = PUBLIC_CORP),
        Doc("k4", "한국전력공사 고객센터", "123", "", address = "서울 중구 예시동 8", category = PUBLIC_CORP),
        isEnd = false,
        total = 20,
    )

    val KEPCO_PAGE2 = page(
        Doc("k5", "한국전력공사 인천본부", "032-000-2005", "인천 남동구 예시로 11", category = PUBLIC_CORP),
        // 1페이지와 같은 장소가 다시 와도 한 번만 보인다
        Doc("k3", "한국전력공사 김포지사", "031-000-2003", "경기 김포시 예시로 7", category = PUBLIC_CORP),
        isEnd = true,
        total = 20,
    )

    /** 카카오 순서는 민원실이 먼저지만, 이름이 정확히 같은 '김포시청'이 맨 앞에 와야 한다 */
    val GIMPO_CITY_HALL = page(
        Doc("g1", "김포시청 민원실", "031-000-3002", "경기 김포시 예시로 9", category = "사회,공공기관 > 행정기관 > 시청"),
        Doc("g2", "김포시청", "031-000-3001", "경기 김포시 예시로 10", category = "사회,공공기관 > 행정기관 > 시청"),
    )

    val PHARMACIES_NEARBY = page(
        Doc("p1", "예시약국", "031-000-4001", "경기 김포시 예시로 12", category = "의료,건강 > 약국", group = "약국", distance = 120),
        Doc("p2", "바로약국", "031-000-4002", "경기 김포시 예시로 13", category = "의료,건강 > 약국", group = "약국", distance = 850),
        Doc("p3", "온누리약국", "031-000-4003", "경기 김포시 예시로 14", category = "의료,건강 > 약국", group = "약국", distance = 1500),
    )

    /** 실제로 확인한 카카오 오류 응답 (키 없음 / 없는 키) */
    const val ERROR_NO_KEY = """{"errorType":"AccessDeniedError","message":"cannot find Authorization : KakaoAK header"}"""
    const val ERROR_BAD_KEY = """{"errorType":"AccessDeniedError","message":"appKey(00000000000000000000000000000000) does not exist"}"""

    /** 카카오 공식 FAQ: 무료 제공량 소진 시 HTTP 400 + 에러 코드 -10 */
    const val ERROR_QUOTA = """{"code":-10,"msg":"API limit has been exceeded."}"""
    /** 403 권한 오류 예시 (카카오맵 사용 설정 OFF). 본문 형식은 추정이며, 앱은 상태 코드 403 으로 판단한다. */
    const val ERROR_DISABLED = """{"errorType":"NotAuthorizedError","message":"App(barodial) disabled OPEN_MAP_AND_LOCAL service."}"""
}
