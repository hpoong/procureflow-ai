# 로컬 DB (PostgreSQL · Flyway)

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
