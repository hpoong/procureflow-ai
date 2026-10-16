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

## SQL 로그 (파라미터 포함)

로컬은 **p6spy**로 `?`가 **실제 값으로 치환된 SQL**을 출력한다.

- 의존성: `com.github.gavlyukovskiy:p6spy-spring-boot-starter`
- `application-local.yaml`: `decorator.datasource.p6spy.enable-logging=true`, `multiline=true`
- 기본(`application.yaml`)은 `enable-logging=false` (운영 유출 방지)

Hibernate `show-sql` / bind TRACE만으로는 완성 SQL이 한눈에 안 나오므로 로컬에서는 p6spy를 쓴다.
