# purchaseorder (발주)

패키지: `com.hopoong.procureflow.purchaseorder` — 이번 범위는 **PO-01 초안 생성**이다.

## purchaseorder(발주).초안 생성

`POST /v1/purchase-orders` — 공급사·품목·사업장·창고를 검증한 `DRAFT` 발주 1건과 라인 N건을 한 트랜잭션에 저장한다.

### 요청

| 헤더 | 설명 |
| --- | --- |
| `Idempotency-Key` | 필수. 1~255자, 공백만 불가 |
| `X-Org-Id`, `X-User-Id` | 필수(UUID). `procureflow.auth.header-actor.enabled=true` 일 때만 요청자로 해석 ([architecture](../../architecture/README.md#요청자-컨텍스트)) |
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
| `lines[].unitPrice` | 필수, `>= 0`, 정수 13·소수 6 |
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
| 401 | `AUTH_REQUIRED` | header-actor 비활성, `X-Org-Id`/`X-User-Id` 누락·UUID 아님 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `(org, 명령, 키)` 가 있고 요청 해시가 다름(작성자가 다른 경우 포함) |
| 422 | `PLANT_NOT_AVAILABLE` | 사업장 없음·다른 조직·`INACTIVE` |
| 422 | `WAREHOUSE_NOT_AVAILABLE` | 창고 없음·다른 조직·`INACTIVE`·요청 사업장 소속 아님 |
| 422 | `SUPPLIER_NOT_AVAILABLE` | 공급사 없음·다른 조직·`INACTIVE` |
| 422 | `ITEM_NOT_PURCHASABLE` | 품목 없음·다른 조직·`INACTIVE`·`purchase_enabled=false` (message 에 lineNo 목록) |
| 500 | `INTERNAL_ERROR` | 그 외. 상세는 ERROR 로그 |

검증 순서: 요청자(401) → 헤더·본문(400) → 멱등 재생/충돌(201·409) → 마스터(422, 사업장→창고→공급사→품목) → 저장.

### 저장

| 테이블 | 초기값 |
| --- | --- |
| `purchase_order` | `status=DRAFT`, `current_revision=1`, `version=0`, `ordered_at`/`approved_at`/`sent_at` NULL, `currency_scale`=통화 기본 소수자리 |
| `purchase_order_revision` | `revision=1`, `revision_status=DRAFT`, `approval_policy_version='UNEVALUATED'`, `before_snapshot` NULL, `after_snapshot`/`content_sha256` |
| `purchase_order_line` | `line_status=ACTIVE`, `closed_qty=0`, `over_receipt_pct`=요청값 또는 0, `effective_revision=1` |
| `purchase_order_audit_log` | `action=CREATE`, `revision=1`, `after_snapshot`=개정의 `after_snapshot` |
| `purchase_order_command` | `CREATE_PURCHASE_ORDER`, 요청 해시, 완료 시 `po_id`·`response_status=201` |

- 저장 순서는 `purchase_order → revision → line` 으로 고정이다(연관 매핑 없이 UUID 컬럼만 매핑). 그래서 `hibernate.order_inserts` 를 켜지 않는다.
- `approval_policy_version='UNEVALUATED'` 는 정책 평가가 상신(PO-04) 시점이라 초안에는 값이 없다는 뜻이다. 상신 때 실제 버전으로 바뀐다.
- DB DEFAULT 가 있는 컬럼도 엔티티 정적 팩토리가 값을 채운다(PostgreSQL 은 명시한 NULL 에 DEFAULT 를 적용하지 않는다).
- 초안 생성은 `outbox_event` 를 기록하지 않는다.

### 발주 번호

`PO-{UTC yyyyMMdd}-{po_number_seq 8자리 0채움}` (예: `PO-20261017-00000001`). 시퀀스(`po_number_seq`, V4)는 전역이라 동시 요청에도 중복이 없고, 실패·롤백된 번호는 재사용되지 않는다(공백 허용). 99,999,999 를 넘으면 자릿수만 늘어난다.

### 멱등성

- 유일 키: `(org_id, command_type, idempotency_key)` (`uq_po_command_idempotency`).
- 요청 해시 = `sha256(canonical({actorUserId, request}))`. 같은 키·같은 본문·같은 작성자면 같은 발주를 돌려준다(201). 같은 키에 본문 또는 작성자가 다르면 409.
- 재생 응답은 **저장된 최초 응답이 아니라 그 발주의 현재 상태**다(응답 본문은 저장하지 않는다). 이후 상신·개정되면 최초 응답과 다를 수 있다.
- 실패한 요청(400·422·500)은 트랜잭션이 롤백되어 기록되지 않는다. 같은 키로 고친 본문을 다시 보내면 새로 처리된다.
- 같은 키 동시 요청: 먼저 들어온 트랜잭션이 끝날 때까지 `command` INSERT 에서 대기하고, 충돌(`uq_po_command_idempotency`)하면 Facade 가 기존 결과를 재생한다. 다른 제약 위반은 그대로 500.

### 스냅샷·해시 canonical 규칙

`PurchaseOrderSnapshotWriter` 하나가 요청 해시와 `after_snapshot`/`content_sha256` 을 만든다.

- 객체 키 사전순(중첩 포함), 공백 없는 compact JSON, `null` 필드 생략
- 모든 숫자는 `stripTrailingZeros().toPlainString()` 문자열(`10`, `10.0`, `1E+1` → `"10"`)
- UUID·날짜·enum → 문자열, 라인은 `lineNo` 순
- DB 의 jsonb 를 읽어 같은 규칙으로 재직렬화한 문자열의 SHA-256(소문자 hex 64자)이 `content_sha256` 과 같다 → 상신(PO-04) 활성화 검증의 전제

### 범위 밖 (후속)

상신·결재(PO-04), 개정(PO-02), 송부(PO-05), 잔량 종결(PO-07), 첨부(PO-08), 조회·목록(PO-10), 초안 수정·삭제, `purchase_order → 마스터` FK, 통화 정밀도 규칙·규격 유효성(PO-03·09). `organization` 마스터가 없어 `X-Org-Id` 조직 존재는 검증하지 않는다(없는 조직이면 사업장 조회가 실패해 `PLANT_NOT_AVAILABLE`).
