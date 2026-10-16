# 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/) 관례를 따른다.

## 형식

```text
type: 짧은 요약

- 변경 내용 (선택)
```

또는 범위를 둘 때:

```text
type(scope): 짧은 요약
```

- 제목은 한 줄, 마침표 없음.
- 본문이 있으면 제목과 빈 줄로 구분한다.
- 요약은 **무엇을 왜** 바꿨는지 드러나게 쓴다.

## type

| type | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 유지 구조 정리 |
| `docs` | 문서만 |
| `test` | 테스트만 |
| `chore` | 빌드·잡무 |

## 규칙

- 목적이 다른 변경은 커밋을 나눈다.
- 한 커밋에 무관한 리팩터·포맷만 섞지 않는다.

## 예시

```text
feat(order): 주문 확정 API 추가

- POST /v1/orders/{id}/confirm
```

```text
fix: 빈 목록 조회 시 500 대신 빈 배열 반환
```

```text
docs: convention 초기 규칙 추가
```

