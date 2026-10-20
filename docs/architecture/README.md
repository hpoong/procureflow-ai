# architecture

## 공통 계약

### 응답 형식

- 성공: **리소스를 최상위에** 둔다(`data` envelope 없음). 생성은 `201 Created`.
- 실패: `{"code": "UPPER_SNAKE", "message": "..."}`. 내부 스택·SQL·클래스명을 노출하지 않는다.
- 업무 예외는 `BusinessException(ErrorCode)` 로 던지고 `GlobalExceptionHandler`(`common/error`)가 변환한다.

| 예외 | 상태 · code |
| --- | --- |
| `BusinessException` | `ErrorCode` 의 상태·code |
| 본문·헤더 검증 실패, JSON 파싱 실패 | 400 `INVALID_REQUEST` |
| Spring MVC 4xx (`ErrorResponse` 구현 예외: 405·415·404 등) | 그 상태 코드 유지, code = `HttpStatus` 이름 (`METHOD_NOT_ALLOWED` …) |
| 그 외 | 500 `INTERNAL_ERROR` (고정 문구, 상세는 ERROR 로그) |

4xx 는 ERROR 로그를 남기지 않는다.

### 요청자 컨텍스트

인증(JWT·Spring Security)이 아직 없어 요청자(조직·사용자)는 **임시 헤더**로 받는다.

- `X-Org-Id`, `X-User-Id` (문자열, 앞뒤 공백 제거 후 1~64자). `RequestActor(String orgId, String userId)` 타입 파라미터를 `RequestActorArgumentResolver` 가 해석한다.
- 조직·사용자 식별자는 외부 인증 체계의 ID 를 그대로 받을 수 있도록 **문자열**이다. DB 컬럼(`org_id`, `created_by`, `updated_by`, `actor_id`, `approver_id`, `delegated_from_id`, `uploaded_by`)도 `varchar(64)` 다(V5). 엔티티 자체 PK·참조 ID(`plant_id` 등)는 UUID 를 유지한다.
- `procureflow.auth.header-actor.enabled=true` 일 때만 동작한다. 공통 기본값 `false`, `application-local.yaml` 만 `true`. **dev/prd 에 true 를 넣지 않는다.**
- 비활성이거나 헤더가 없거나 공백뿐이거나 64자를 넘으면 401 `AUTH_REQUIRED`. 헤더 값은 로그에 남기지 않는다.
- **임시:** 현재 리졸버는 헤더 대신 고정값 `Org-001` / `User-001` 을 돌려준다(TODO). 헤더 해석·401 검증은 고정값 제거 후 다시 동작한다.
- 인증 도입 시 이 리졸버를 교체하고 설정 키를 제거한다.

### 시간

`Clock` 빈(`config/ClockConfig`, UTC)을 주입해 쓴다. 시각 컬럼은 `Instant`(`timestamptz`), 저장 시 마이크로초로 자른다.
