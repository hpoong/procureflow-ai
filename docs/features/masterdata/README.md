# masterdata (기준정보)

패키지: `com.hopoong.procureflow.masterdata` (`domain`, `repository`)

## masterdata(기준정보).최소 마스터

발주 생성(PO-01) 검증에 필요한 최소 컬럼만 둔 테이블이다(`V3__master_data.sql`). 관리 API·UOM·통화·공급사-품목 관계는 후속이며, 지금은 **SQL 로 시드**한다([local-db](../../guides/local-db.md#로컬-qa-시드)).

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
