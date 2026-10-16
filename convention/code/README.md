# Java 코딩

Google Java Style · Oracle Code Conventions · Effective Java 관례를 따른다.

## 이름

- 패키지: 소문자만 (`com.hopoong.procureflow.order`)
- 클래스: UpperCamelCase (`OrderService`)
- 메서드·필드: lowerCamelCase (`findById`)
- 상수: UPPER_SNAKE (`MAX_RETRY`)
- 이름은 역할을 드러낸다. `Util`, `Helper`, `Manager`만으로 끝내지 않는다.

## 스타일

- 들여쓰기 공백 4칸 (또는 팀 포매터 설정). 탭과 섞지 않는다.
- import는 와일드카드(`*`)를 쓰지 않는다.
- public API가 아니면 불필요하게 `public`으로 열지 않는다.
- 매직 넘버·매직 스트링은 상수나 enum으로 뺀다.

## 객체·불변

- 가능하면 불변 객체·`final` 필드를 선호한다 (Effective Java).
- 생성자보다 정적 팩토리(`of`, `from`, `valueOf`)가 의도를 더 잘 드러내면 그걸 쓴다.
- null을 넓게 흘리지 않는다. Optional은 반환 타입에만 쓰고, 필드·파라미터에는 쓰지 않는다.

## 예외

- 복구 가능한 업무 오류와 시스템 오류를 구분한다.
- 예외를 삼키지 않는다. 로그 후 재던지거나, 호출자가 처리할 수 있는 예외로 감싼다.
- `e.printStackTrace()` / `System.out` 금지. 로거는 [logging](../logging/README.md).

## 체크리스트

- [ ] 이름·패키지가 규칙과 맞는가?
- [ ] 불필요한 public·와일드카드 import가 없는가?
- [ ] null·예외 처리가 명확한가?
