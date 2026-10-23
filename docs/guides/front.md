# 로컬 화면 (front)

`front/` — 기준정보(사업장·창고·공급사·품목) 등록·목록, 발주 생성, 발주 개정·이력 화면. 빌드 도구·npm 의존성 없는 순수 HTML/CSS/JS 다. 상단 메뉴로 세 페이지를 오간다.

| 파일 | 역할 |
| --- | --- |
| `front/index.html` | 기준정보 화면 골격 |
| `front/app.js` | 기준정보 탭·등록 폼·목록 (ES module). 리소스별 필드·컬럼은 `RESOURCES` 에 정의 |
| `front/purchase-order.html` | 발주 생성 화면 골격 |
| `front/purchase-order.js` | 거래처·품목·거래 조건 폼, 금액 요약, `POST /v1/purchase-orders` 호출, 최근 생성 목록 (ES module). 품목 칸은 `LINE_FIELDS` 에 정의(`place: row` 는 표, `detail` 은 상세) |
| `front/purchase-order-revision.html` | 발주 개정 초안 작성과 개정 이력·상세 화면 골격 |
| `front/purchase-order-revision.js` | 발주 ID 조회, 현재 유효본 편집, 신규·취소 라인 구성, 개정 생성과 전·후 스냅샷 표시 (ES module) |
| `front/styles.css` | 세 화면 공통 스타일 |
| `front/server.mjs` | 로컬 전용 개발 서버. 정적 파일 제공 + `/v1/*` 를 백엔드로 프록시 |

## 실행

1. 백엔드를 `local` 프로파일로 띄운다(헤더 요청자 `procureflow.auth.header-actor.enabled=true` 가 local 에만 켜져 있다).
2. 저장소 루트에서 `node front/server.mjs` (Node 18 이상).
3. `http://127.0.0.1:5173` (기준정보), `http://127.0.0.1:5173/purchase-order.html` (발주 생성), `http://127.0.0.1:5173/purchase-order-revision.html` (발주 개정) 접속.

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `FRONT_PORT` | `5173` | 화면 서버 포트 (`127.0.0.1` 에만 바인딩) |
| `API_TARGET` | `http://localhost:8080` | 프록시할 백엔드 주소 |

화면과 API 가 같은 출처로 보이므로 백엔드에 CORS 설정이 필요 없다. 백엔드가 꺼져 있으면 프록시가 502 `API_UNREACHABLE` 을 돌려주고 화면에 안내가 뜬다.

## 사용

상단 **조직 ID / 사용자 ID** 가 `X-Org-Id` / `X-User-Id` 헤더로 나간다. 기본값은 QA 시드와 같은 `Org-001` / `User-001` 이고 브라우저 `localStorage`(`procureflow.actor`)에 저장되어 세 화면이 같이 쓴다.

### 기준정보

- 탭마다 왼쪽은 등록 폼, 오른쪽은 조직 목록(코드·이름 검색, 상태 필터). 목록의 ID 버튼을 누르면 UUID 가 복사된다(발주 생성 요청에 쓸 때).
- 창고는 등록된 사업장 중에서 소속을 고른다. 사업장이 없으면 등록할 수 없다.
- 서버 400 메시지(`field: 사유`)는 해당 필드 아래에, 409·422 등은 폼 아래 알림으로 보여준다.

API 계약은 [masterdata](../features/masterdata/README.md#masterdata기준정보등록목록-api).

### 발주 생성

- 화면은 ① 거래처·입고지 → ② 품목 → ③ 거래 조건(선택) 순서다. 오른쪽 **발주 요약**에 공급사·입고지·공급가액·세액·합계(화면 계산, 표시용)와 생성 버튼이 있다.
- 공급사·사업장·품목은 기준정보 목록 API 로 불러온 선택 목록이고, 창고는 선택한 사업장 소속만 보인다. 비활성·구매 불가 항목도 표시만 해 두고 고를 수 있다(서버 422 확인용).
- 품목 표에는 품목·수량·단위·단가·금액만 두고, 세금·납기·입고 경로·단위 환산·초과 입고는 **상세**에 둔다(기본값 `VAT10`·`0.1`·`STANDARD`·`EA`·1:1). 상세 칸에 오류가 나면 자동으로 펼친다. 입고 경로가 검사 입고(`INSPECTION`)일 때만 검사 규격 개정 ID 칸이 나온다.
- 수량·단가·환율 등 숫자는 입력 문자열 그대로 JSON 숫자로 보내 소수 정밀도를 지킨다.
- `Idempotency-Key` 는 화면에 보이지 않는다. 같은 요청자·같은 본문이면 같은 키를 다시 쓰므로 응답 유실 후 재전송이나 성공 뒤 재클릭은 기존 발주로 재생된다. 본문이 바뀌면 새 키, **처음부터 다시**를 누르면 키를 버린다. `X-Request-Id` 는 요청마다 새 UUID.
- 서버 400 의 `lines[0].orderedQty: 사유` 는 해당 품목 칸 아래에, 409·422 는 요약 패널 알림으로 보여준다.
- **최근 생성한 발주**는 발주 조회 API 가 없어 브라우저 `sessionStorage` 에만 남는다(같은 발주 재생은 한 건으로 갱신).

API 계약은 [purchaseorder](../features/purchaseorder/README.md#purchaseorder발주초안-생성).

### 발주 개정

- 발주 조회·목록(PO-10)은 아직 범위 밖이므로 발주 UUID 를 직접 입력해 개정 이력을 조회한다. 현재 유효 개정의 상세 스냅샷을 불러와 입고지·거래 조건·진행 중 라인을 편집 폼에 채운다.
- 개정 작성 API가 요구하는 `expectedVersion` 은 개정 이력 응답에 없으므로 화면에서 직접 입력한다. 409 `PO_VERSION_CONFLICT` 가 나면 최신 version 을 확인해 다시 입력해야 한다.
- 기존 진행 중 라인은 유지하거나 취소할 수 있고, 신규 품목 라인을 추가할 수 있다. 기존 라인의 품목은 변경할 수 없다. 모든 기존 진행 중 라인은 유지·취소 중 한 곳에 포함된다.
- 이력에서 개정을 선택하면 저장된 `beforeSnapshot` / `afterSnapshot` 을 나란히 표시한다. 개정 1 은 변경 전 스냅샷이 없다.
- 개정 초안을 저장해도 현재 유효 발주와 입고 기준은 바뀌지 않는다. 같은 요청 본문은 같은 `Idempotency-Key` 로 재전송한다.

API 계약은 [purchaseorder 개정](../features/purchaseorder/README.md#purchaseorder발주개정-초안-작성)과 [개정 이력 조회](../features/purchaseorder/README.md#purchaseorder발주개정-이력-조회).
