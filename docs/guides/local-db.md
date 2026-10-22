# 로컬 DB (PostgreSQL · Flyway · JPA)

## 기동

```bash
docker compose up -d
```

헬스체크 통과 후 앱을 띄운다 (`./gradlew bootRun`).

## 설정 키

| 키 / 환경 변수 | 설명 |
| --- | --- |
| `DB_URL` | JDBC URL (`jdbc:postgresql://…`) |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `spring.flyway.locations` | `classpath:db/migration` (기본) |

접속 정보는 `application-local.yaml`에 두고, `local` 기본값은 `docker-compose.yml`과 동일하다.  
운영(`prd` 등) 프로파일에서는 환경 변수로만 주입하고 yaml에 비밀을 두지 않는다.

## 마이그레이션

- 스크립트: `src/main/resources/db/migration/V{version}__{desc}.sql`
- 이미 적용된 파일은 수정하지 않는다. 변경은 새 버전 파일로 추가한다.
- JPA `ddl-auto`는 `validate`다. 테이블 변경은 Flyway로만 한다.
- 현재 버전: V1 baseline, V2 발주 스키마, V3 마스터 최소 테이블(`plant`·`warehouse`·`supplier`·`item`), V4 발주 번호 시퀀스(`po_number_seq`), V5 조직·사용자 식별자 컬럼 `uuid` → `varchar(64)`.

## 임시 요청자 헤더

`procureflow.auth.header-actor.enabled` — 인증 도입 전 임시. `true`(local 만)일 때 `X-Org-Id` / `X-User-Id` 헤더를 요청자로 쓴다. 공통 기본값은 `false`이고 운영에 켜지 않는다. ([architecture](../architecture/README.md#요청자-컨텍스트))

## 로컬 QA 시드

아래 고정 ID 가 필요하면 SQL 로 넣는다(local 전용, Flyway 에 넣지 않음). 임의 데이터는 [기준정보 화면](front.md)이나 `/v1/plants` 등 등록 API 로 넣어도 된다(ID 는 서버가 만든다). 조직·사용자는 문자열 ID, 마스터는 UUID 다. 고정값:

| 구분 | ID | code / 상태 |
| --- | --- | --- |
| QA 조직 O1 | `Org-001` | - |
| QA 조직 O2 | `Org-002` | - |
| QA 사용자 U1 / U2 | `User-001` / `User-002` | - |
| plant P1 (O1) | `10000000-0000-0000-0000-000000000001` | `qa-plant-01` ACTIVE |
| warehouse W1 (O1, P1) | `20000000-0000-0000-0000-000000000001` | `qa-wh-01` ACTIVE |
| supplier S1 (O1) | `30000000-0000-0000-0000-000000000001` | `qa-sup-01` ACTIVE |
| supplier S2 (O1) | `30000000-0000-0000-0000-000000000002` | `qa-sup-02` INACTIVE |
| supplier S9 (O2) | `30000000-0000-0000-0000-000000000009` | `qa-sup-09` ACTIVE |
| item I1 (O1) | `40000000-0000-0000-0000-000000000001` | `qa-item-01` ACTIVE, purchase_enabled=true |
| item I2 (O1) | `40000000-0000-0000-0000-000000000002` | `qa-item-02` ACTIVE, purchase_enabled=false |

```sql
-- 재실행 안전: ON CONFLICT (id) DO NOTHING
INSERT INTO plant (id, org_id, plant_code, name, status, created_by, created_at, updated_by, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'Org-001', 'qa-plant-01', 'QA Plant 01', 'ACTIVE',
        'User-001', now(), 'User-001', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO warehouse (id, org_id, plant_id, warehouse_code, name, status, created_by, created_at, updated_by, updated_at)
VALUES ('20000000-0000-0000-0000-000000000001', 'Org-001', '10000000-0000-0000-0000-000000000001',
        'qa-wh-01', 'QA Warehouse 01', 'ACTIVE',
        'User-001', now(), 'User-001', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO supplier (id, org_id, supplier_code, name, status, created_by, created_at, updated_by, updated_at)
VALUES ('30000000-0000-0000-0000-000000000001', 'Org-001', 'qa-sup-01', 'QA Supplier 01', 'ACTIVE',
        'User-001', now(), 'User-001', now()),
       ('30000000-0000-0000-0000-000000000002', 'Org-001', 'qa-sup-02', 'QA Supplier 02', 'INACTIVE',
        'User-001', now(), 'User-001', now()),
       ('30000000-0000-0000-0000-000000000009', 'Org-002', 'qa-sup-09', 'QA Supplier 09', 'ACTIVE',
        'User-001', now(), 'User-001', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO item (id, org_id, item_code, name, status, purchase_enabled, created_by, created_at, updated_by, updated_at)
VALUES ('40000000-0000-0000-0000-000000000001', 'Org-001', 'qa-item-01', 'QA Item 01', 'ACTIVE', true,
        'User-001', now(), 'User-001', now()),
       ('40000000-0000-0000-0000-000000000002', 'Org-001', 'qa-item-02', 'QA Item 02', 'ACTIVE', false,
        'User-001', now(), 'User-001', now())
ON CONFLICT (id) DO NOTHING;
```

정리(FK 역순, QA 조직 O1·O2 의 발주만. 마스터 시드는 남긴다). 사용자가 local DB 에서 직접 실행한다.

```sql
DELETE FROM purchase_order_audit_log WHERE po_id IN (SELECT id FROM purchase_order WHERE org_id IN ('Org-001', 'Org-002'));
DELETE FROM purchase_order_command   WHERE org_id IN ('Org-001', 'Org-002');
DELETE FROM purchase_order_line      WHERE po_id IN (SELECT id FROM purchase_order WHERE org_id IN ('Org-001', 'Org-002'));
DELETE FROM purchase_order_revision  WHERE org_id IN ('Org-001', 'Org-002');
DELETE FROM purchase_order           WHERE org_id IN ('Org-001', 'Org-002');
```

## 통합 테스트 (Testcontainers)

`*IT` 는 Docker 가 있어야 돈다(없으면 skip). Colima 를 쓰면 `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` 과 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` 을 지정한다.

## SQL 로그 (파라미터 포함)

로컬은 **p6spy**로 `?`가 **실제 값으로 치환된 SQL**을 출력한다.

- 의존성: `com.github.gavlyukovskiy:p6spy-spring-boot-starter`
- `application-local.yaml`: `decorator.datasource.p6spy.enable-logging=true`, `logging=sysout`
- 기본(`application.yaml`)은 `enable-logging=false` (운영 유출 방지)
- 형식: `src/main/resources/spy.properties` 의 `logMessageFormat` → `config/P6SpySqlFormatter`, 시각은 `dateformat`. 첫 줄에 시각·소요 ms·category·connection, 다음 줄부터 Hibernate 포맷터(`FormatStyle.BASIC`, DDL 은 `DDL`)로 줄바꿈·들여쓰기한 SQL

Hibernate `show-sql` / bind TRACE만으로는 완성 SQL이 한눈에 안 나오므로 로컬에서는 p6spy를 쓴다.

## DB MCP (Cursor)

`.cursor/mcp.json` 의 `procureflow-postgres` 가 `@henkey/postgres-mcp-server`(npx)로 로컬 DB(`docker-compose.yml` 계정)에 붙는다. Cursor 설정 → MCP 에서 켜고, Node 18 이상이 필요하다. 조회뿐 아니라 쓰기·DDL 도구도 열려 있으므로 local DB 에만 연결한다.
