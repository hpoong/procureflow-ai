# masterdata (기준정보)

패키지: `api.masterdata` (`controller`, `service`, `model`), `domain.entity.masterdata`, `domain.repository.masterdata` (기본 패키지 `com.hopoong.procureflow` 생략).

## masterdata(기준정보).최소 마스터

발주 생성(PO-01) 검증에 필요한 최소 컬럼만 둔 테이블이다(`V3__master_data.sql`). UOM·통화·공급사-품목 관계는 후속이다. 등록·목록은 아래 API 와 [기준정보 화면](../../guides/front.md)으로 하고, 로컬 QA 고정값은 SQL 로 시드할 수 있다([local-db](../../guides/local-db.md#로컬-qa-시드)).

| 테이블 | 엔티티 | 비고 |
| --- | --- | --- |
| `plant` | `Plant` | 사업장 |
| `warehouse` | `Warehouse` | 창고. `(plant_id, org_id)` → `plant(id, org_id)` 복합 FK 로 소속 사업장과 같은 조직 보장 |
| `supplier` | `Supplier` | 공급사 |
| `item` | `Item` | 품목. `purchase_enabled` 포함 |

- 공통 컬럼: `id`, `org_id`, `{plant,warehouse,supplier,item}_code`(조직 내 유일), `name`, `status`, `created_by/at`, `updated_by/at`, `version`.
- `status`: `ACTIVE` / `INACTIVE`. **활성** = `status='ACTIVE'`.
- **거래 가능 품목** = `status='ACTIVE' AND purchase_enabled=true`.
- 모든 조회는 `org_id` 조건을 포함한다. 다른 조직의 데이터는 "없음"과 같다.
- `UNIQUE (id, org_id)` 는 이후 `purchase_order → 마스터` 조직 일치 복합 FK 를 위한 준비다. 이번에는 발주 쪽 FK 를 추가하지 않는다.

## masterdata(기준정보).등록·목록 API

`MasterDataController` → `MasterDataService`. 헤더 `X-Org-Id`, `X-User-Id` 필수([요청자 컨텍스트](../../architecture/README.md#요청자-컨텍스트)). `org_id` 는 요청자 조직, `created_by`·`updated_by` 는 요청자 사용자로 서버가 채운다.

| 메서드 · 경로 | 본문 (위반 → 400 `INVALID_REQUEST`) |
| --- | --- |
| `POST /v1/plants` | `plantCode` 필수 ≤32, `name` 필수 ≤255, `status` 필수 |
| `POST /v1/warehouses` | `plantId` 필수 UUID, `warehouseCode` 필수 ≤32, `name` 필수 ≤255, `status` 필수 |
| `POST /v1/suppliers` | `supplierCode` 필수 ≤64, `name` 필수 ≤255, `status` 필수 |
| `POST /v1/items` | `itemCode` 필수 ≤64, `name` 필수 ≤255, `status` 필수, `purchaseEnabled` 필수 boolean |
| `GET /v1/{plants,warehouses,suppliers,items}` | 없음. 요청자 조직 전체를 코드순으로 돌려준다(페이지 없음) |

- 코드·이름은 앞뒤 공백을 제거하고 저장한다. 공백뿐이면 400.
- 성공: 등록 `201 Created`, 목록 `200 OK`. 리소스(목록은 배열)를 최상위에 둔다. 응답 필드는 엔티티 컬럼과 같고 `orgId` 는 내려주지 않는다.
- 수정·비활성화·삭제 API 는 아직 없다.

| 상황 | 상태 · code |
| --- | --- |
| 조직 내 코드 중복 (동시 등록으로 unique 제약에 걸린 경우 포함) | 409 `{PLANT,WAREHOUSE,SUPPLIER,ITEM}_CODE_DUPLICATED` |
| 창고의 `plantId` 가 요청자 조직에 없음 | 422 `PLANT_NOT_FOUND` |

소속 사업장의 활성 여부는 창고 등록 시 검사하지 않는다(발주 생성 시 검사).
