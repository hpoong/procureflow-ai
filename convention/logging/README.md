# 로깅

SLF4J · OWASP Logging Cheat Sheet 관례를 따른다.

## 사용

- `org.slf4j.Logger` / Lombok `@Slf4j`만 쓴다.
- `System.out`, `printStackTrace` 금지.
- 메시지에 `{}` 자리표시자를 쓴다. 문자열 `+` 연결로 로그를 만들지 않는다.

```java
log.info("order confirmed orderId={}", orderId);
log.error("failed to charge orderId={}", orderId, e);
```

## 레벨

| 레벨 | 언제 |
| --- | --- |
| ERROR | 즉시 조치가 필요한 장애 |
| WARN | 비정상이지만 요청은 처리 가능 |
| INFO | 중요한 업무 사건 (생성·확정·외부 호출 결과) |
| DEBUG | 개발·장애 분석용 상세 |

- 사용자 입력 오류(검증 실패)에 ERROR를 쓰지 않는다.
- 반복문 안에서 건마다 INFO를 남기지 않는다.

## 민감정보

- 비밀번호, 토큰, 카드번호, 주민번호, 비밀키를 로그에 남기지 않는다.
- 필요하면 마스킹한다. 요청/응답 본문 전체를 기본 로그로 찍지 않는다.

## 요청 추적

- 요청 ID를 MDC에 넣고 로그에 남긴다. 요청 종료 시 MDC를 비운다.
- 비동기/배치에서는 컨텍스트가 끊기지 않게 ID를 넘긴다.

## 체크리스트

- [ ] SLF4J + `{}` 를 쓰는가?
- [ ] 레벨이 적절한가?
- [ ] 민감정보가 없는가?
