# API 명세

`api` 모듈의 springdoc-openapi가 Spring MVC Controller와 요청·응답 DTO를 분석해 OpenAPI 문서를 생성합니다.

공용 요청·응답 초안은 [getddo-spec의 도메인별 API 문서](https://github.com/GETDDO/getddo-spec/tree/main/05-api)를 따릅니다. 백엔드 구현 검토는 [내부 처리 흐름](internal-flows.md), [검증 항목](verification.md), [API·DB 대응](../03-database/api-schema-mapping.md)에 기록합니다.

GD-52의 요청 헤더와 Controller 사용법은 [시연용 사용자 문맥과 내 정보 API](user-profile.md)에 정리합니다.

추첨 API 리뷰의 경로·요청·응답과 결정 사항은 [SPEC 추첨 API](https://github.com/GETDDO/getddo-spec/blob/main/05-api/drawing.md)로 이관했습니다. 백엔드 저장·트랜잭션의 판단 근거와 남은 설계 문제는 [추첨 저장 ADR](../04-decisions/0003-drawing-snapshots-and-publications.md)에 기록합니다.

GD-55의 내부 Service 호출과 트랜잭션 규칙은 [감사 로그 기록 기능](audit-recording.md)에 정리합니다.

## 접속

DB 연결 등 애플리케이션 실행 준비를 마친 뒤 `./gradlew :api:bootRun`으로 실행합니다.
기본 포트 기준 접속 주소는 다음과 같습니다.

| 문서 | 주소 |
| --- | --- |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |

Swagger UI 진입 주소는 `/swagger-ui/index.html`로 이동합니다.
현재 사용자·알림·관리자 이벤트 Controller의 API가 표시됩니다. `@CurrentUser User`를 받는 API에는 공통 사용자 헤더 입력란이 자동으로 추가됩니다.

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
./gradlew :api:integrationTest --tests 'com.getddo.api.common.config.OpenApiConfigTest'
./gradlew test
./gradlew build
```

라이브러리 설정은 [springdoc 공식 문서](https://springdoc.org/)를 참고합니다.
