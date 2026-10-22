# 기준정보 화면 (front)

`front/` — 사업장·창고·공급사·품목을 등록하고 목록을 보는 화면. 빌드 도구·npm 의존성 없는 순수 HTML/CSS/JS 다.

| 파일 | 역할 |
| --- | --- |
| `front/index.html` | 화면 골격 |
| `front/styles.css` | 스타일 |
| `front/app.js` | 탭·등록 폼·목록 (ES module). 리소스별 필드·컬럼은 `RESOURCES` 에 정의 |
| `front/server.mjs` | 로컬 전용 개발 서버. 정적 파일 제공 + `/v1/*` 를 백엔드로 프록시 |

## 실행

1. 백엔드를 `local` 프로파일로 띄운다(헤더 요청자 `procureflow.auth.header-actor.enabled=true` 가 local 에만 켜져 있다).
2. 저장소 루트에서 `node front/server.mjs` (Node 18 이상).
3. `http://127.0.0.1:5173` 접속.

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `FRONT_PORT` | `5173` | 화면 서버 포트 (`127.0.0.1` 에만 바인딩) |
| `API_TARGET` | `http://localhost:8080` | 프록시할 백엔드 주소 |

화면과 API 가 같은 출처로 보이므로 백엔드에 CORS 설정이 필요 없다. 백엔드가 꺼져 있으면 프록시가 502 `API_UNREACHABLE` 을 돌려주고 화면에 안내가 뜬다.

## 사용

- 상단 **조직 ID / 사용자 ID** 가 `X-Org-Id` / `X-User-Id` 헤더로 나간다. 기본값은 QA 시드와 같은 `Org-001` / `User-001` 이고 브라우저 `localStorage` 에 저장된다.
- 탭마다 왼쪽은 등록 폼, 오른쪽은 조직 목록(코드·이름 검색, 상태 필터). 목록의 ID 버튼을 누르면 UUID 가 복사된다(발주 생성 요청에 쓸 때).
- 창고는 등록된 사업장 중에서 소속을 고른다. 사업장이 없으면 등록할 수 없다.
- 서버 400 메시지(`field: 사유`)는 해당 필드 아래에, 409·422 등은 폼 아래 알림으로 보여준다.

API 계약은 [masterdata](../features/masterdata/README.md#masterdata기준정보등록목록-api).
