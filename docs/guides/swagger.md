# Swagger UI (springdoc-openapi)

## 로컬

앱 기동 후:

- UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

`application-local.yaml`에서만 `springdoc.*.enabled=true` 이다.  
기본(`application.yaml`)은 비활성이라 운영 프로파일에 실수로 공개되지 않는다.

## 의존성

- `org.springdoc:springdoc-openapi-starter-webmvc-ui` (Spring Boot 4 호환)
- API 메타: `com.hopoong.procureflow.config.OpenApiConfig`
