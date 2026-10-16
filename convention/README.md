# convention

`procureflow-ai` 공통 개발 규칙.  
업계에서 널리 쓰이는 관례를 이 저장소용으로 짧게 정리했다.

**한 번에 전부 읽지 않는다.** 아래 표에서 필요한 문서만 연다.

## 작업별 문서

| 작업 | 문서 |
| --- | --- |
| Java 이름·스타일 | [code](code/README.md) |
| 패키지·계층·트랜잭션 | [spring](spring/README.md) |
| REST API·컨트롤러 | [endpoint](endpoint/README.md) |
| 응답·에러 | [response](response/README.md) |
| 설정·프로파일 | [config](config/README.md) |
| 로깅 | [logging](logging/README.md) |
| 인증·비밀·입력 검증 | [security](security/README.md) |
| 테스트 | [test](test/README.md) |
| 커밋 메시지 | [commit](commit/commit-convention.md) |
| 브랜치·PR | [git](git/README.md) |

## 적용 원칙

- 이미 다른 방식으로 짜인 코드는 일괄 고치지 않는다. **새로 쓰거나 고치는 코드부터** 적용한다.
- 보안·데이터 안전 규칙이 다른 규칙과 충돌하면 보안·안전을 우선한다.
- 프로젝트 설계·기능 설명은 `docs/`, 코딩 규칙은 여기(`convention/`)다.
