# Spring

Spring Boot 권장 구조와 일반적인 계층형 관례를 따른다.

## 패키지

기본 패키지: `com.hopoong.procureflow`

```text
com.hopoong.procureflow
├── api/
│   └── {domain}/      예: purchaseorder
│       ├── controller
│       ├── service
│       └── model      요청·응답 DTO
├── domain/
│   ├── entity/
│   │   └── {domain}/  JPA 엔티티·enum
│   └── repository/
│       └── {domain}/  Spring Data 리포지토리
├── common/            둘 이상 도메인이 쓰는 것만
└── config/
```

- 최상위는 **계층**(`api`, `domain`)으로 나누고, 그 아래를 **도메인(기능) 이름**으로 나눈다.
- 같은 도메인은 계층이 달라도 같은 이름을 쓴다 (`api.purchaseorder`, `domain.entity.purchaseorder`).
- `domain`은 `api`를 참조하지 않는다. 다른 도메인의 엔티티·리포지토리는 `api.{domain}.service`에서 가져다 쓸 수 있다.
- API 가 없는 도메인(예: `masterdata`)은 `domain` 쪽만 둔다.
- `common`은 도메인을 참조하지 않는다. 한 도메인만 쓰는 코드를 미리 `common`에 올리지 않는다.
- 순환 의존을 만들지 않는다.

## 계층 책임

| 계층 | 책임 |
| --- | --- |
| controller | HTTP 입출력, 검증 어노테이션, 상태 코드 |
| service | 업무 규칙, 트랜잭션 경계 |
| model | 요청·응답·도메인 표현 |

- 컨트롤러에 업무 로직을 두지 않는다.
- 서비스에서 HttpServletRequest 등 웹 타입에 직접 의존하지 않는다.

## 트랜잭션

- 쓰기·여러 DB 접근이 있는 서비스 메서드에 `@Transactional`을 둔다.
- 조회만이면 `readOnly = true`.
- 트랜잭션 안에서 외부 HTTP·느린 I/O를 호출하지 않는다. 필요하면 트랜잭션 밖으로 분리한다.

## DI

- 생성자 주입을 기본으로 한다. 필드 `@Autowired`는 쓰지 않는다.
- 필수 의존은 생성자로, 선택 의존만 신중히 선택 주입한다.

## 체크리스트

- [ ] 계층(`api` / `domain.entity` / `domain.repository`) 아래 도메인 이름으로 나뉘었는가?
- [ ] 계층 책임이 섞이지 않았는가?
- [ ] 트랜잭션·생성자 주입이 올바른가?
