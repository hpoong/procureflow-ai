# 설정

Twelve-Factor App (III. Config) · Spring Boot Externalized Configuration 관례를 따른다.

## 파일

```text
src/main/resources/
├── application.yaml           공통 기본값
├── application-local.yaml     로컬
├── application-dev.yaml       개발 서버 (필요 시)
└── application-prd.yaml       운영 (필요 시)
```

- 확장자는 `.yaml`로 통일한다.
- 모든 환경에 같은 값은 `application.yaml`에만 둔다. 프로파일마다 복사하지 않는다.

## 프로파일

- 이름: `local`, `dev`, `stg`, `prd` 정도를 사용한다.
- 기본(미지정)은 `local`. 운영은 `SPRING_PROFILES_ACTIVE=prd`를 명시한다.
- 코드에 `if (profile == "prd")` 분기를 두지 않는다. 설정 값으로 차이를 준다.

## 비밀·환경 변수

- 비밀번호·API 키·JWT 서명은 **파일에 넣지 않는다.** `${ENV_NAME}`으로 받는다.
- 환경 변수 이름은 UPPER_SNAKE (`DB_URL`, `DB_PASSWORD`).
- 필수 값이 없으면 기동 실패하게 한다 (약한 기본값 금지).

## 체크리스트

- [ ] 비밀이 yaml에 없는가?
- [ ] 환경별 값만 프로파일에 있는가?
- [ ] 운영 프로파일이 명시되는가?
