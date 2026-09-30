# CORS 설정

- 관련 작업: GD-53
- 적용 경로: `/api/v1/**`
- 사용자 입력 계약: [시연용 사용자 문맥과 내 정보 API](user-profile.md)

프론트와 API의 origin이 다를 때 브라우저가 사용자 헤더를 보내고 성공·오류 응답을 읽도록 설정한다. origin은 프로토콜·호스트·포트의 조합이다. API 경로나 마지막 `/`를 넣지 않고 프론트 페이지의 실제 origin을 등록한다.

## 기본 정책

| 속성 (`getddo.cors`) | 기본값 |
| --- | --- |
| `allowed-origins` | `CORS_ALLOWED_ORIGINS` 환경변수. 미설정·빈 값은 허용 origin 없음 |
| `allowed-methods` | `GET`, `HEAD`, `POST`, `PUT`, `DELETE` |
| `allowed-headers` | `Content-Type`, `X-User-ID`, `X-User-Role`, `X-User-Membership` |
| `exposed-headers` | 빈 목록 |
| `allow-credentials` | `false` |

정책은 `api/src/main/resources/application.yaml`에서 관리하고 origin만 환경별로 주입한다. `*`가 포함된 origin은 credentials 값에 관계없이 앱 시작 시 거절한다. 주소를 아직 모르면 비워 둔 채 실행하고, 정해진 뒤 환경변수를 설정하고 앱을 재시작한다. 주소 변경에 Java 코드 수정이나 재빌드는 필요하지 않다.

현재 쿠키를 사용하지 않는 전제로 credentials를 끈다. 사용자 헤더를 보내는 데 credentials 허용은 필요하지 않다. 쿠키 기반 기능을 도입하면 프론트 설정과 함께 재검토한다. `Idempotency-Key`, `Retry-After`, `PATCH`는 소비 기능의 계약이 정해지면 목록을 갱신한다.

`exposed-headers`는 프론트 JavaScript가 추가로 읽을 **응답 헤더** 목록이다. 예를 들어 `response.headers.get('Retry-After')`가 필요하면 해당 헤더를 추가한다. JSON 본문의 `data`, `code`, `message`를 읽는 데에는 추가 노출 설정이 필요 없다.

## 환경변수 전달

아래 주소는 설정 형식만 설명하는 테스트용 예시다. 실제 프론트·시연 주소가 아니며 프로젝트 기본 허용 목록에도 포함하지 않는다.

```dotenv
CORS_ALLOWED_ORIGINS=https://frontend.example.test,https://demo.example.test:8443
```

- Docker Compose: 기존 DB 설정이 있는 `.env`에 값을 지정한다. `compose.yaml`의 app `environment`가 이 값을 컨테이너로 전달한다. 수정 후 `docker compose up -d --build app`으로 적용한다.
- 로컬 `bootRun`: `.env`를 자동으로 읽지 않는다. IDE 실행 환경변수 또는 셸 환경변수에 직접 설정한다. DB 설정은 [로컬 실행 안내](../../README.md)를 따른다.

```bash
export CORS_ALLOWED_ORIGINS='https://frontend.example.test'
./gradlew :api:bootRun --args='--spring.profiles.active=local'
```

```powershell
$env:CORS_ALLOWED_ORIGINS = 'https://frontend.example.test'
.\gradlew.bat :api:bootRun --args='--spring.profiles.active=local'
```

## 프론트 요청과 오류 확인

`apiBaseUrl`, `selectedUserId`, `selectedMembership`에는 실제 실행 환경과 DB에 등록된 USER 값을 사용한다.

```javascript
const response = await fetch(`${apiBaseUrl}/api/v1/users/me`, {
  headers: {
    'X-User-ID': selectedUserId,
    'X-User-Role': 'USER',
    'X-User-Membership': selectedMembership,
  },
});
const body = await response.json();
// response.ok / response.status와 body.code로 성공·업무 오류를 구분한다.
```

ADMIN은 `X-User-Role: ADMIN`과 등록된 관리자 ID를 보내고 membership은 생략할 수 있다. 사용자 정보·역할·멤버십 대조 규칙은 기존 GD-52 구현을 따른다. CORS는 호출자의 신원을 인증하거나 업무별 권한을 대신 검사하지 않는다.

브라우저 개발자 도구의 Network에서 다음 두 요청을 구분한다.

1. **사전 요청(OPTIONS)**: `Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers`로 사용할 메서드와 헤더 이름을 알린다. 사용자 헤더의 실제 값은 필요하지 않고 사용자 DB 조회도 발생하지 않는다. 정상 응답은 2xx와 일치하는 `Access-Control-Allow-Origin`, 허용 메서드·헤더를 포함한다.
2. **실제 요청**: CORS 허용 후 기존 사용자 검증과 업무 처리를 수행한다. 허용 origin에서 발생한 MVC의 400·401·403·409·500도 CORS 헤더를 유지하므로 프론트가 오류 봉투를 읽을 수 있다. 같은 origin 또는 Origin 없는 요청은 기존 처리 흐름을 따른다.

유효한 preflight는 OPTIONS와 Origin, Access-Control-Request-Method의 조합으로 판단한다. 단순히 OPTIONS라는 이유로 모든 요청을 허용하지 않는다. `allowed-methods`는 본 요청의 메서드를 허용하는 목록이므로 preflight를 위해 OPTIONS를 추가할 필요는 없다.

미허용 origin·메서드 또는 허용되지 않은 헤더만 요청한 preflight는 Spring의 CORS 처리기가 403으로 거절한다. 이 거절은 MVC 밖에서 처리하므로 공통 JSON 오류 봉투를 약속하지 않으며 브라우저에서도 응답 본문을 읽을 수 없다. 허용·미허용 헤더를 함께 요청하면 Spring은 허용한 헤더만 응답할 수 있다. HTTP 200이어도 요청한 헤더 일부가 `Access-Control-Allow-Headers`에 없으면 브라우저는 실제 요청을 보내지 않는다.

CORS에서 허용한 DELETE 등의 메서드라도 해당 Controller 경로가 없거나 업무 권한·조건을 충족하지 않으면 요청은 성공하지 않는다.

## 검증 범위와 후속 작업

```bash
./gradlew :api:test --tests 'com.getddo.api.common.config.Cors*Test'
./gradlew test
./gradlew build
```

Windows에서는 `gradlew.bat`를 사용한다. 전체 빌드의 MySQL 통합 테스트에는 Docker가 필요하다.

자동 테스트는 실제 YAML 바인딩·Spring 설정·CorsFilter와 GD-52의 Controller/resolver/service/오류 처리기를 사용하고 사용자 Repository만 대체한다. 수동으로 MockMvc에 등록한 필터의 검증이며 실제 서블릿 컨테이너 등록과 전체 DB 연결 검증은 구분한다.

실제 프론트 origin이 아직 없어 브라우저 연결 확인은 수행하지 않았다. GD-54에서 실제 애플리케이션 등록·MySQL 연결과 브라우저의 성공/오류 JSON 읽기를 확인한다. curl이나 MockMvc 성공을 브라우저 검증 완료로 표시하지 않는다.

기존 공용 사용자·알림 명세와 GD-52 입력 계약의 차이는 [사용자 문맥 문서](user-profile.md)에 기록되어 있다. 이 CORS 설정은 현재 헤더를 전달할 수 있게 하며 공용 계약을 새로 확정하지 않는다.
