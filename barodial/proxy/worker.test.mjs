// 실행: node --test proxy/worker.test.mjs   (Node 18+ 내장 테스트 실행기, 추가 설치 없음)
import { test } from "node:test";
import assert from "node:assert/strict";
import { handle } from "./worker.js";

const KEY = "0123456789abcdef0123456789abcdef";

function fakeFetch(status = 200, body = '{"meta":{"is_end":true},"documents":[]}') {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    return new Response(body, { status, headers: { "Content-Type": "application/json;charset=UTF-8" } });
  };
  fn.calls = calls;
  return fn;
}

const req = (path, headers = {}) => new Request(`https://proxy.example.com${path}`, { headers });

test("카카오 키를 붙여 허용된 파라미터만 전달한다", async () => {
  const f = fakeFetch();
  const res = await handle(req("/v2/local/search/keyword.json?query=%EA%B9%80%ED%8F%AC%EC%8B%9C%EC%B2%AD&page=2&size=15&evil=1"), { KAKAO_REST_API_KEY: KEY }, f);
  assert.equal(res.status, 200);
  assert.equal(res.headers.get("Cache-Control"), "no-store");
  assert.equal(f.calls.length, 1);
  const sent = new URL(f.calls[0].url);
  assert.equal(sent.origin + sent.pathname, "https://dapi.kakao.com/v2/local/search/keyword.json");
  assert.equal(sent.searchParams.get("query"), "김포시청");
  assert.equal(sent.searchParams.get("page"), "2");
  assert.equal(sent.searchParams.get("evil"), null);
  assert.equal(f.calls[0].init.headers.Authorization, `KakaoAK ${KEY}`);
  assert.equal(f.calls[0].init.cache, "no-store");
});

test("APP_TOKEN 이 있으면 토큰이 맞아야 한다", async () => {
  const f = fakeFetch();
  const env = { KAKAO_REST_API_KEY: KEY, APP_TOKEN: "secret-token" };
  const denied = await handle(req("/v2/local/search/keyword.json?query=a"), env, f);
  assert.equal(denied.status, 401);
  assert.equal((await denied.json()).errorType, "ProxyTokenError");
  const wrong = await handle(req("/v2/local/search/keyword.json?query=a", { "X-BaroDial-Token": "nope" }), env, f);
  assert.equal(wrong.status, 401);
  assert.equal(f.calls.length, 0);
  const ok = await handle(req("/v2/local/search/keyword.json?query=a", { "X-BaroDial-Token": "secret-token" }), env, f);
  assert.equal(ok.status, 200);
});

test("다른 경로·검색어 없음·키 미설정은 카카오를 부르지 않는다", async () => {
  const f = fakeFetch();
  assert.equal((await handle(req("/v2/local/search/address.json?query=a"), { KAKAO_REST_API_KEY: KEY }, f)).status, 404);
  assert.equal((await handle(req("/v2/local/search/keyword.json"), { KAKAO_REST_API_KEY: KEY }, f)).status, 400);
  assert.equal((await handle(req("/v2/local/search/keyword.json?query=a"), {}, f)).status, 500);
  assert.equal(f.calls.length, 0);
});

test("카카오 오류 상태·본문을 그대로 전달한다 (한도 초과 400/-10)", async () => {
  const body = '{"code":-10,"msg":"API limit has been exceeded."}';
  const res = await handle(req("/v2/local/search/keyword.json?query=a"), { KAKAO_REST_API_KEY: KEY }, fakeFetch(400, body));
  assert.equal(res.status, 400);
  assert.equal(await res.text(), body);
});

test("카카오에 닿지 못하면 502", async () => {
  const failing = async () => { throw new Error("network down"); };
  const res = await handle(req("/v2/local/search/keyword.json?query=a"), { KAKAO_REST_API_KEY: KEY }, failing);
  assert.equal(res.status, 502);
});
