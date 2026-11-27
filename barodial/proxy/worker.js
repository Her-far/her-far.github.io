/**
 * 바로전화 온라인 상호 검색용 경량 프록시 (Cloudflare Workers).
 *
 * - 카카오 REST API 키는 이 서버의 비밀 값(KAKAO_REST_API_KEY)에만 둔다. 앱(APK)에는 키가 없다.
 * - 앱은 GET /v2/local/search/keyword.json?query=... 로 부르고, 이 서버가 카카오에 같은 요청을 보낸 뒤 응답을 그대로 돌려준다.
 * - 허용한 파라미터만 넘기고, 다른 카카오 API는 열어 두지 않는다.
 * - 선택: APP_TOKEN 을 설정하면 X-BaroDial-Token 헤더가 같은 요청만 받는다.
 * - 카카오 운영 정책(검색 결과 저장 금지)에 따라 응답을 캐시하거나 기록하지 않는다. 검색어도 로그로 남기지 않는다.
 */

const KAKAO_KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
const PATH = "/v2/local/search/keyword.json";
const ALLOWED_PARAMS = ["query", "category_group_code", "x", "y", "radius", "rect", "page", "size", "sort"];
const TOKEN_HEADER = "X-BaroDial-Token";

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

/** 길이가 다른 문자열도 같은 시간에 비교 (토큰 추측 방지) */
function safeEqual(a, b) {
  const x = new TextEncoder().encode(a ?? "");
  const y = new TextEncoder().encode(b ?? "");
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

export async function handle(request, env, fetchImpl = fetch) {
  const url = new URL(request.url);
  if (request.method !== "GET") return json(405, { errorType: "MethodNotAllowed", message: "GET only" });
  if (url.pathname !== PATH) return json(404, { errorType: "NotFound", message: "unknown path" });
  if (!env.KAKAO_REST_API_KEY) return json(500, { errorType: "ProxyMisconfigured", message: "KAKAO_REST_API_KEY is not set" });
  if (env.APP_TOKEN && !safeEqual(request.headers.get(TOKEN_HEADER), env.APP_TOKEN)) {
    return json(401, { errorType: "ProxyTokenError", message: "invalid proxy token" });
  }

  const query = url.searchParams.get("query");
  if (!query || query.trim().length === 0 || query.length > 100) {
    return json(400, { errorType: "InvalidArgument", message: "query is required" });
  }

  const target = new URL(KAKAO_KEYWORD_URL);
  for (const name of ALLOWED_PARAMS) {
    const value = url.searchParams.get(name);
    if (value !== null) target.searchParams.set(name, value);
  }

  let upstream;
  try {
    upstream = await fetchImpl(target.toString(), {
      method: "GET",
      headers: { Authorization: `KakaoAK ${env.KAKAO_REST_API_KEY}`, Accept: "application/json" },
      cache: "no-store",
    });
  } catch (e) {
    return json(502, { errorType: "UpstreamUnavailable", message: "cannot reach Kakao" });
  }
  // 상태 코드와 본문을 그대로 전달한다 (401/403/400(-10)/429 등을 앱이 구분해 안내한다).
  return new Response(await upstream.text(), {
    status: upstream.status,
    headers: {
      "Content-Type": upstream.headers.get("Content-Type") ?? "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

export default {
  fetch(request, env) {
    return handle(request, env);
  },
};
