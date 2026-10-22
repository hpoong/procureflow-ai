# purchaseorder (발주)

패키지: `api.purchaseorder` (`controller`, `service`, `model`), `domain.entity.purchaseorder`, `domain.repository.purchaseorder` (기본 패키지 `com.hopoong.procureflow` 생략) — 이번 범위는 **PO-01 초안 생성**, **PO-02 개정 초안 작성·개정 이력 조회**, **PO-03 통화 정밀도 단가 검증**이다.

## purchaseorder(발주).초안 생성

`POST /v1/purchase-orders` — 공급사·품목·사업장·창고를 검증한 `DRAFT` 발주 1건과 라인 N건을 한 트랜잭션에 저장한다. 기능 설명(검토자용)은 [PO-01](PO-01/README.md).

### 요청

| 헤더 | 설명 |
| --- | --- |
| `Idempotency-Key` | 필수. 1~255자, 공백만 불가 |
| `X-Org-Id`, `X-User-Id` | 필수(문자열, 1~64자). `procureflow.auth.header-actor.enabled=true` 일 때만 요청자로 해석 ([architecture](../../architecture/README.md#요청자-컨텍스트)) |
| `X-Request-Id` | 선택. UUID 면 감사 로그 `correlation_id`, 아니면 서버가 새로 만든다(거절 안 함) |

| 필드 | 규칙 (위반 → 400 `INVALID_REQUEST`) |
| --- | --- |
| `plantId`, `supplierId`, `shipToWarehouseId` | 필수 UUID |
| `currency` | 필수, `^[A-Z]{3}$`, `java.util.Currency` 로 인식되고 기본 소수자리 0~6 |
| `exchangeRate` | 선택, `> 0`, 정수 10·소수 10자리 |
| `paymentTermsCode` / `incotermsCode` / `incotermsPlace` | 선택, 길이 ≤50 / ≤10 / ≤255 |
| `paymentTermsSnapshot` | 선택, JSON object 만 |
| `lines` | 필수, 1개 이상 (상한 없음) |
| `lines[].itemId` | 필수 UUID |
| `lines[].orderedQty` | 필수, `> 0`, 정수 13·소수 6 |
| `lines[].purchaseUom`, `inventoryUom` | 필수, 공백 불가, ≤20 |
| `lines[].conversionNumerator`, `conversionDenominator` | 필수, `> 0` |
| `lines[].unitPrice` | 필수, `>= 0`, 정수 13·소수 6. 유효 소수 자릿수(`stripTrailingZeros`) ≤ 요청 통화 소수 자릿수 — 위반은 422 `PRICE_PRECISION_INVALID` |
| `lines[].taxCode` / `taxRate` | 필수, ≤50 / 0~1(정수 3·소수 6) |
| `lines[].requestedDate`, `promisedDate` | 선택 |
| `lines[].receiptRouting` | 필수, `DIRECT` / `STANDARD` / `INSPECTION` |
| `lines[].inspectionSpecRevisionId` | `INSPECTION` 이면 필수, 그 외에는 없어야 함 |
| `lines[].overReceiptPct` | 선택(null → 0), 0~100, 정수 3·소수 2 |

본문의 `orgId` 같은 알 수 없는 필드는 무시한다. `id`·`poNumber`·`currencyScale`·`status`·`currentRevision`·`orgId`·`createdBy`·시각·`lineNo`(배열 순서 1..n)는 서버가 정한다.

### 응답

성공 `201 Created`, 리소스 최상위(envelope 없음), `Location` 없음(조회 API 없음).

```json
{
  "id": "uuid", "poNumber": "PO-20261017-00000001", "status": "DRAFT",
  "currentRevision": 1, "version": 0, "createdAt": "2026-10-17T13:00:00Z",
  "lines": [ { "id": "uuid", "lineNo": 1 }, { "id": "uuid", "lineNo": 2 } ]
}
```

| 상태 | code | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 본문·헤더 검증 실패, JSON 파싱 실패, `Idempotency-Key` 누락 |
| 401 | `AUTH_REQUIRED` | header-actor 비활성, `X-Org-Id`/`X-User-Id` 누락·공백·64자 초과 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `(org, 명령, 키)` 가 있고 요청 해시가 다름(작성자가 다른 경우 포함) |
| 422 | `PLANT_NOT_AVAILABLE` | 사업장 없음·다른 조직·`INACTIVE` |
| 422 | `WAREHOUSE_NOT_AVAILABLE` | 창고 없음·다른 조직·`INACTIVE`·요청 사업장 소속 아님 |
| 422 | `SUPPLIER_NOT_AVAILABLE` | 공급사 없음·다른 조직·`INACTIVE` |
| 422 | `ITEM_NOT_PURCHASABLE` | 품목 없음·다른 조직·`INACTIVE`·`purchase_enabled=false` (message 에 lineNo 목록) |
| 422 | `PRICE_PRECISION_INVALID` | 단가 유효 소수 자릿수 > 통화 소수 자릿수(예: KRW `1500.5`). message 에 lineNo 목록, 금액 값은 담지 않음 |
| 500 | `INTERNAL_ERROR` | 그 외. 상세는 ERROR 로그 |

검증 순서: 요청자(401) → 헤더·본문(400) → 멱등 재생/충돌(201·409) → 단가 정밀도(422, `PurchaseOrderTermsValidator`) → 마스터(422, 사업장→창고→공급사→품목) → 저장.

### 저장

| 테이블 | 초기값 |
| --- | --- |
| `purchase_order` | `status=DRAFT`, `current_revision=1`, `version=0`, `ordered_at`/`approved_at`/`sent_at` NULL, `currency_scale`=통화 기본 소수자리 |
| `purchase_order_revision` | `revision=1`, `revision_status=DRAFT`, `approval_policy_version='UNEVALUATED'`, `before_snapshot` NULL, `after_snapshot`/`content_sha256` |
| `purchase_order_line` | `line_status=ACTIVE`, `closed_qty=0`, `over_receipt_pct`=요청값 또는 0, `effective_revision=1` |
| `purchase_order_audit_log` | `action=CREATE`, `revision=1`, `after_snapshot`=개정의 `after_snapshot` |
| `purchase_order_command` | `CREATE_PURCHASE_ORDER`, 요청 해시, 완료 시 `po_id`·`response_status=201`, `po_revision_id` NULL(재생에 개정 참조가 필요 없음) |

- 저장 순서는 `purchase_order → revision → line` 으로 고정이다(연관 매핑 없이 ID 컬럼만 매핑). 그래서 `hibernate.order_inserts` 를 켜지 않는다.
- `approval_policy_version='UNEVALUATED'` 는 정책 평가가 상신(PO-04) 시점이라 초안에는 값이 없다는 뜻이다. 상신 때 실제 버전으로 바뀐다.
- DB DEFAULT 가 있는 컬럼도 엔티티 정적 팩토리가 값을 채운다(PostgreSQL 은 명시한 NULL 에 DEFAULT 를 적용하지 않는다).
- 초안 생성은 `outbox_event` 를 기록하지 않는다.

### 발주 번호

`PO-{UTC yyyyMMdd}-{po_number_seq 8자리 0채움}` (예: `PO-20261017-00000001`). 시퀀스(`po_number_seq`, V4)는 전역이라 동시 요청에도 중복이 없고, 실패·롤백된 번호는 재사용되지 않는다(공백 허용). 99,999,999 를 넘으면 자릿수만 늘어난다.

### 멱등성

- 유일 키: `(org_id, command_type, idempotency_key)` (`uq_po_command_idempotency`).
- 요청 해시 = `sha256(canonical({actorUserId, request}))`. 같은 키·같은 본문·같은 작성자면 같은 발주를 돌려준다(201). 같은 키에 본문 또는 작성자가 다르면 409.
- 재생 응답은 **저장된 최초 응답이 아니라 그 발주의 현재 상태**다(응답 본문은 저장하지 않는다). 이후 상신·개정되면 최초 응답과 다를 수 있다. 예: 개정 초안 작성 후 같은 키로 초안 생성을 재요청하면 증가한 현재 `version` 이 응답된다.
- 실패한 요청(400·422·500)은 트랜잭션이 롤백되어 기록되지 않는다. 같은 키로 고친 본문을 다시 보내면 새로 처리된다.
- 같은 키 동시 요청: 먼저 들어온 트랜잭션이 끝날 때까지 `command` INSERT 에서 대기하고, 충돌(`uq_po_command_idempotency`)하면 Facade 가 기존 결과를 재생한다. 다른 제약 위반은 그대로 500.

### 스냅샷·해시 canonical 규칙

`PurchaseOrderSnapshotWriter` 하나가 요청 해시와 `after_snapshot`/`content_sha256` 을 만든다.

- 객체 키 사전순(중첩 포함), 공백 없는 compact JSON, `null` 필드 생략
- 모든 숫자는 `stripTrailingZeros().toPlainString()` 문자열(`10`, `10.0`, `1E+1` → `"10"`)
- UUID·날짜·enum → 문자열(조직·사용자 ID 는 원래 문자열), 라인은 `lineNo` 순
- DB 의 jsonb 를 읽어 같은 규칙으로 재직렬화한 문자열의 SHA-256(소문자 hex 64자)이 `content_sha256` 과 같다 → 상신(PO-04) 활성화 검증의 전제

## purchaseorder(발주).개정 초안 작성

`POST /v1/purchase-orders/{poId}/revisions` — 승인 이후 발주(`APPROVED`·`SENT`·`PARTIALLY_RECEIVED`)의 변경 제안을 새 개정(`DRAFT`)으로 저장한다. **현재 유효본(`purchase_order` 조건·`current_revision`, `purchase_order_line`)은 바꾸지 않는다.** 개정의 상신·승인·활성화는 PO-04 다. 기능 설명(검토자용)은 [PO-02](PO-02/README.md).

### 요청

헤더는 초안 생성과 같다(`Idempotency-Key` 필수 1~255자, `X-Request-Id` 선택, `X-Org-Id`·`X-User-Id`).

| 필드 | 규칙 (위반 → 400 `INVALID_REQUEST`) |
| --- | --- |
| `expectedVersion` | 필수, `>= 0`. 개정 기준으로 읽은 발주 `version` |
| `changeReasonCode` | 필수, 공백 불가, ≤50 |
| `changeReasonDetail` | 선택, ≤1000 |
| `shipToWarehouseId` | 필수 UUID |
| `exchangeRate`, `paymentTermsCode`, `paymentTermsSnapshot`, `incotermsCode`, `incotermsPlace` | 초안 생성과 같은 규칙. **헤더 조건 전체 교체** — 생략·null 은 "값 없음" |
| `lines` | 필수 1개 이상. 각 항목은 초안 생성 `lines[]` 와 같은 필드·규칙 + `lineId`(선택 UUID) |
| `cancelLineIds` | 선택 UUID 목록(null → 빈 목록) |

- `supplierId`·`plantId`·`currency` 는 개정할 수 없다(필드 없음, 보내면 무시). 기존 라인의 `itemId` 도 바꿀 수 없다.
- 라인 규칙(위반 → 422 `REVISION_LINE_INVALID`, message 에 사유와 `lineId`):
  1. `lineId` 를 준 라인은 이 발주의 `ACTIVE` 라인이고 `itemId` 가 현재 값과 같아야 한다.
  2. `lineId` 없음 = 신규 라인. `lineNo` 는 이 발주 전체 라인의 최대 `line_no` + 1 부터 요청 순서대로.
  3. 기존 `ACTIVE` 라인은 `lines[].lineId` 와 `cancelLineIds` 를 합쳐 **정확히 한 번** 나와야 한다(누락·중복·양쪽 지정 거절). `cancelLineIds` 는 `ACTIVE` 라인만.
  4. 유지 라인 `orderedQty >= closed_qty`. (GRN 미구현이라 수령량 기준 검사는 아직 없다.)
- 단가 정밀도(PO-03 규칙, 위반 → 422 `PRICE_PRECISION_INVALID`): **신규 라인과 단가가 현재 값과 다른(`compareTo ≠ 0`) 유지 라인만** 검사한다. 단가를 그대로 둔 유지 라인은 규칙 도입 전 값이어도 검사하지 않는다. message 의 `lineNo` 는 요청 `lines` 순번.
- 마스터: `shipToWarehouseId` 가 현재와 다를 때만 창고(활성·같은 조직·발주 사업장 소속), 신규 라인 품목(활성·구매 가능)을 검사한다. 공급사·사업장·기존 라인 품목은 다시 보지 않는다.

### 응답

성공 `201 Created`.

```json
{
  "poId": "uuid", "poNumber": "PO-20261017-00000001",
  "revisionId": "uuid", "revision": 2, "revisionStatus": "DRAFT",
  "currentRevision": 1, "poVersion": 1, "createdAt": "2026-10-20T04:00:00Z"
}
```

| 상태 | code | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 본문·헤더 검증, JSON 파싱, `poId` 가 UUID 아님 |
| 401 | `AUTH_REQUIRED` | 초안 생성과 같음 |
| 404 | `PURCHASE_ORDER_NOT_FOUND` | 발주 없음 또는 다른 조직(존재 여부를 구분하지 않음) |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `(org, CREATE_PURCHASE_ORDER_REVISION, 키)` 에 요청 해시 다름 |
| 409 | `PO_VERSION_CONFLICT` | `expectedVersion` ≠ 현재 `version` |
| 409 | `PO_NOT_REVISABLE` | 발주 상태가 `APPROVED`·`SENT`·`PARTIALLY_RECEIVED` 아님 |
| 409 | `OPEN_REVISION_EXISTS` | `DRAFT`·`PENDING_APPROVAL` 개정이 이미 있음 |
| 422 | `REVISION_LINE_INVALID` | 라인 규칙 1~4 |
| 422 | `PRICE_PRECISION_INVALID` | 신규·단가 변경 라인의 단가 소수 자릿수 > 통화 소수 자릿수 |
| 422 | `WAREHOUSE_NOT_AVAILABLE` / `ITEM_NOT_PURCHASABLE` | 바뀐 창고 / 신규 라인 품목 사용 불가 |
| 422 | `REVISION_NO_CHANGE` | 제안이 현재 유효본과 같음 |

검증 순서: 요청자(401) → 헤더·본문(400) → 멱등 재생/충돌(201·409) → [트랜잭션] 발주(404) → version(409) → 상태(409) → 진행 중 개정(409) → 라인(422) → 단가 정밀도(422) → 마스터(422) → 변경 없음(422) → 저장.

### 저장

| 테이블 | 쓰기 |
| --- | --- |
| `purchase_order_command` | INSERT `CREATE_PURCHASE_ORDER_REVISION`, 완료 시 `po_id`·`po_revision_id`(V6)·`response_status=201` |
| `purchase_order` | `version` +1, `updated_at` 만 UPDATE. `current_revision`·`status`·조건 컬럼은 그대로 |
| `purchase_order_revision` | INSERT `revision`=최대+1, `DRAFT`, `approval_policy_version='UNEVALUATED'`, `change_reason_code/detail`, `before_snapshot`=현재 유효 개정의 `after_snapshot`(저장값 그대로), `after_snapshot`=제안, `content_sha256` |
| `purchase_order_audit_log` | INSERT `action=REVISION_CREATE`, `revision`=n, `reason_code`, before·after = 개정 행과 같음, `correlation_id` |

`purchase_order_line` 은 쓰지 않는다. 신규·취소 라인은 개정 스냅샷에만 있고, 활성화(PO-04) 때 라인에 반영된다.

- 동시성: `command` INSERT → 발주 행 `SELECT … FOR UPDATE` 순서다. 같은 발주의 개정 작성은 행 잠금으로 직렬화되고, 뒤 요청은 커밋된 새 `version` 을 읽어 409 `PO_VERSION_CONFLICT`. 같은 키 동시 요청은 `command` INSERT 에서 대기 후 재생된다.
- 멱등: 요청 해시 = `sha256(canonical({actorUserId, poId, request}))`. 재생 응답은 `po_revision_id` 로 찾은 그 개정과 발주의 **현재 상태**다.
- **진행 중 개정은 발주당 1개**다. 잘못 만든 `DRAFT` 개정을 취소하는 API 는 PO-04 전까지 없다. local 에서는 [local-db 진행 중 개정 정리](../../guides/local-db.md#진행-중-개정-정리) SQL 로 정리한다.

### 개정 스냅샷

`PurchaseOrderSnapshotWriter.revisionSnapshot` 이 초안 `after_snapshot` 과 **같은 키·같은 canonical 규칙**으로 만든다. 값의 출처만 다르다.

- 헤더: `id`·`orgId`·`poNumber`·`plantId`·`supplierId`·`currency`·`currencyScale`·`createdBy`·`createdAt`·`status` = 현재 발주, `shipToWarehouseId`·`exchangeRate`·`paymentTerms*`·`incoterms*` = 제안값, `currentRevision` = n.
- 라인: 이 발주의 모든 라인을 `lineNo` 순으로. 유지 라인 = 기존 `id`·`lineNo`·`closedQty` + 제안값, 신규 = 새 UUID·새 `lineNo`·`closedQty=0`·`ACTIVE`, 취소 = `lineStatus=CANCELLED`·나머지 기존값, 기존 `CLOSED`/`CANCELLED` = 그대로. 모든 라인 `effectiveRevision` = n, `overReceiptPct` 생략 → 0.
- `header.status`·`header.currentRevision`·`lines[].effectiveRevision` 은 **스냅샷 메타**다. `before_snapshot` 과 `after_snapshot` 을 비교할 때 이 키의 차이는 업무 변경이 아니다.
- 변경 없음(`REVISION_NO_CHANGE`) 판정은 `before_snapshot` 이 아니라, 같은 n 으로 현재 유효본을 직렬화한 스냅샷과 제안 스냅샷의 문자열 비교다. 그래서 `10` vs `10.000`, `overReceiptPct` 생략 vs `0` 은 같은 값으로 본다.

## purchaseorder(발주).개정 이력 조회

| API | 응답 |
| --- | --- |
| `GET /v1/purchase-orders/{poId}/revisions` | 200 `{ poId, poNumber, currentRevision, revisions[] }`, `revision` 내림차순. 항목: `revisionId`, `revision`, `revisionStatus`, `changeReasonCode`, `changeReasonDetail`, `contentSha256`, `createdBy`, `createdAt`, `submittedAt`, `approvedAt`, `rejectedAt` |
| `GET /v1/purchase-orders/{poId}/revisions/{revision}` | 200 목록 항목 필드 + `beforeSnapshot`(object, 개정 1 은 null), `afterSnapshot`(object) |

| 상태 | code | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | `poId` 가 UUID 아님, `revision` 이 정수 아님 |
| 401 | `AUTH_REQUIRED` | 초안 생성과 같음 |
| 404 | `PURCHASE_ORDER_NOT_FOUND` | 발주 없음 또는 다른 조직 |
| 404 | `REVISION_NOT_FOUND` | 상세의 개정 번호 없음 |

## 범위 밖 (후속)

상신·결재와 개정 활성화·반려·취소(PO-04), 송부(PO-05), 잔량 종결(PO-07), 첨부(PO-08), 조회·목록(PO-10), 초안 수정·삭제, `purchase_order → 마스터` FK, 규격 유효성(PO-09). `organization` 마스터가 없어 `X-Org-Id` 조직 존재는 검증하지 않는다(없는 조직이면 사업장 조회가 실패해 `PLANT_NOT_AVAILABLE`).
