# API 명세

`api` 모듈의 springdoc-openapi가 Spring MVC Controller와 요청·응답 DTO를 분석해 OpenAPI 문서를 생성합니다.

## 접속

DB 연결 등 애플리케이션 실행 준비를 마친 뒤 `./gradlew :api:bootRun`으로 실행합니다.
기본 포트 기준 접속 주소는 다음과 같습니다.

| 문서 | 주소 |
| --- | --- |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |

Swagger UI 진입 주소는 `/swagger-ui/index.html`로 이동합니다.
현재 업무 Controller가 없으므로 실제 API 목록은 기능 구현 후 표시됩니다.

## 자동 문서 생성

- `com.getddo.api` 아래의 Controller를 문서화하며 API 그룹은 통합 문서 하나로 시작합니다.
- `@GetMapping`, `@PostMapping`, `@RequestBody` 등 Spring MVC 선언과 DTO 타입에서 API 경로·요청·응답 구조를 추출합니다.
- `@Tag`, `@Operation`, `@Schema` 같은 Swagger 전용 어노테이션은 필수가 아닙니다.
- 응답은 기존 `ResponseEnvelope<실제 응답 DTO>`를 사용합니다. 구체적인 타입을 선언해야 `data`의 내부 구조도 문서화할 수 있습니다.
- 실행 중 결정되는 업무 오류 코드와 모든 가능한 오류 응답이 자동으로 추론되는 것은 아닙니다.

문서 제목·버전·설명은 [OpenApiConfig](../../api/src/main/java/com/getddo/api/common/config/OpenApiConfig.java),
검색 범위와 표시 순서는 [application.yaml](../../api/src/main/resources/application.yaml)에서 관리합니다.
사용자·관리자 문서 그룹은 API 경로 규칙이 정해지면 분리합니다.

## 검증

Swagger 전용 어노테이션이 없는 테스트 전용 Controller로 UI 접근과 요청·공통 응답 스키마의 자동 생성을 검증합니다.

```bash
./gradlew :api:test --tests 'com.getddo.api.common.config.OpenApiConfigTest'
./gradlew test
./gradlew build
```

라이브러리 설정은 [springdoc 공식 문서](https://springdoc.org/)를 참고합니다.
