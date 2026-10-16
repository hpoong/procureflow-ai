# API · 엔드포인트

일반적인 REST / HTTP API 관례를 따른다.

## URL

- 복수형 명사 리소스: `/v1/orders`, `/v1/orders/{id}`
- 동사를 URL에 넣지 않는다. 동작은 HTTP 메서드로 표현한다.
- 버전은 경로 prefix (`/v1/...`)를 기본으로 한다.
- 소문자 kebab-case: `/v1/sales-orders`

## HTTP 메서드

| 메서드 | 용도 |
| --- | --- |
| GET | 조회 (안전·멱등) |
| POST | 생성·비멱등 처리 |
| PUT | 전체 교체 (멱등) |
| PATCH | 부분 수정 |
| DELETE | 삭제 (멱등 권장) |

## 요청

- 본문은 JSON. Content-Type `application/json`.
- 식별자·필터는 path/query. 비밀·민감정보는 query에 두지 않는다.
- Bean Validation (`@Valid`, `@NotNull` 등)으로 서버에서 검증한다.

## 컨트롤러

- 얇게 유지한다. 변환·위임만 하고 업무는 서비스에 맡긴다.
- 성공/실패 HTTP 상태는 [response](../response/README.md)를 따른다.

## 체크리스트

- [ ] URL이 리소스·복수형·버전 규칙과 맞는가?
- [ ] 메서드가 의미와 맞는가?
- [ ] 입력 검증이 서버에 있는가?
