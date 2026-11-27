# 바로전화 온라인 검색 프록시

카카오 REST API 키를 **휴대폰(APK)이 아니라 서버에만** 두고 싶을 때 쓰는 아주 작은 중계 서버입니다.
Cloudflare Workers 무료 요금제로 돌릴 수 있습니다.

```
바로전화 앱 ──(검색어, 토큰)──▶ 프록시 ──(검색어 + KakaoAK 키)──▶ dapi.kakao.com
```

- 받는 요청: `GET /v2/local/search/keyword.json?query=…` 하나뿐입니다. 다른 카카오 API는 열려 있지 않습니다.
- 넘기는 파라미터: `query, category_group_code, x, y, radius, rect, page, size, sort`만 넘깁니다. 나머지는 버립니다.
- `APP_TOKEN`을 설정하면 `X-BaroDial-Token` 헤더가 같은 요청만 받습니다. 주소가 알려져도 남이 내 쿼터를 쓰지 못하게 하려면 설정하세요.
- 응답은 캐시하지 않고(`Cache-Control: no-store`), 검색어를 로그로 남기지 않습니다. 카카오 운영 정책상 검색 결과를 저장할 수 없기 때문입니다.
- 카카오가 돌려준 상태 코드와 본문을 그대로 전달합니다. 앱은 이것으로 키 오류(401), 권한 오류(403), 한도 초과(400/-10, 429)를 구분해 안내합니다.

## 배포 (Cloudflare Workers)

PC에 Node.js 18 이상이 있어야 합니다.

```bat
cd barodial\proxy
npx wrangler login
npx wrangler secret put KAKAO_REST_API_KEY
npx wrangler secret put APP_TOKEN
npx wrangler deploy
```

- `secret put`을 실행하면 값을 입력하라고 나옵니다. 카카오 REST API 키와, 직접 정한 긴 임의 문자열(토큰)을 넣으세요.
- 배포가 끝나면 `https://barodial-place-proxy.<계정>.workers.dev` 같은 주소가 나옵니다.
- 바로전화에서 `설정 › 온라인 상호·기관 검색 › 연결 설정 › 프록시 서버`를 고르고 이 주소와 토큰을 넣은 뒤 **[연결 확인]**을 누르세요.

## 테스트

```bat
node --test proxy\worker.test.mjs
```

이 테스트는 카카오를 실제로 부르지 않고 가짜 응답으로 확인합니다. 키 붙이기, 파라미터 거르기, 토큰 검사, 오류 전달을 봅니다.
