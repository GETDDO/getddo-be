# 시연용 사용자 문맥과 내 정보 API

- 관련 작업: [GD-52](https://ureca4.atlassian.net/browse/GD-52)
- 공용 기준: [사용자 API](https://github.com/GETDDO/getddo-spec/blob/main/05-api/user.md), [공통 API 초안](https://github.com/GETDDO/getddo-spec/blob/main/05-api/common.md)

이 문서는 GD-52의 백엔드 구현 사용법이다. 로그인 없이 사전 등록한 사용자를 헤더로 선택하고, DB에서 확인한 정보를 필요한 Controller에 전달한다. 사용자 정보를 변경하거나 로그인·토큰을 발급하지 않는다.

공용 `common.md`의 기존 초안은 role 헤더를 받지 않고 멤버십을 선택으로 두고 있다. GD-52는 2026-09-30 사용자 확인에 따라 아래 입력을 구현했다. 후속 사용자 요청에 따라 알림 N01~N03과 관리자 이벤트 등록도 같은 MVC 처리에 연결했다. 기존 알림 명세의 선택 멤버십·역할 헤더 미사용·사용자 오류 코드와 차이가 있으므로 프론트엔드 호출과 공용 명세를 함께 맞춰야 한다. 이 문서는 백엔드 구현 변경을 기록하며 공용 명세의 확정 상태를 대신 변경하지 않는다.

## U01 호출

`GET /api/v1/users/me`

| 헤더 | USER | ADMIN | 입력 |
| --- | --- | --- | --- |
| `X-User-ID` | 필수 | 필수 | 등록된 사용자 UUID, 하이픈을 포함한 36자 표기 |
| `X-User-Role` | 필수 | 필수 | `USER` 또는 `ADMIN`, DB 역할과 대조 |
| `X-User-Membership` | 필수 | 선택 | `excellent`, `vip`, `vvip` |

각 헤더는 하나의 값만 허용한다. 빈 문자열·공백·중복·잘못된 값은 형식 오류로 처리한다. UUID의 영문 대소문자는 허용하며 역할은 대문자, 멤버십은 소문자다. USER는 DB 멤버십과 일치해야 한다. ADMIN이 멤버십을 보내면 형식만 검사하며 응답에는 DB 값을 사용한다.

```http
GET /api/v1/users/me
X-User-ID: 00000000-0000-0000-0000-000000000521
X-User-Role: USER
X-User-Membership: vip
```

예제 UUID는 형식 예시다. 자동 생성되는 시연 계정이 아니므로 실제 DB에 준비한 ID를 사용한다. ADMIN 요청은 등록된 관리자 ID와 `X-User-Role: ADMIN`을 전달하면 된다.

성공 시 기존 `ResponseEnvelope`의 `data`에 [공용 UserProfile](https://github.com/GETDDO/getddo-spec/blob/main/05-api/user.md#본인-정보)의 7개 필드만 반환한다. 응답은 헤더 문자열을 복사하지 않고 DB 조회 결과로 만든다. 멤버십은 소문자로 변환하며, nullable 값은 필드를 생략하지 않고 `null`로 반환한다. 전화번호의 앞자리 0과 ADMIN의 DB 멤버십도 보존한다.

## 오류 응답

오류는 기존 `GlobalExceptionHandler`와 실패 `ResponseEnvelope`를 사용한다.

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | `COMMON-005` | UUID·role·membership 형식 오류, 빈 값, 중복 헤더 |
| 401 | `USER-003` | 필요한 사용자 헤더 누락 |
| 401 | `USER-002` | DB에 등록되지 않은 사용자 |
| 403 | `USER-004` | 헤더와 DB의 역할 불일치 |
| 403 | `USER-005` | USER의 DB 멤버십이 null |
| 409 | `USER-006` | USER의 헤더와 DB 멤버십 불일치 |
| 403 | `USER-007` | INACTIVE 사용자의 내 정보 조회 |
| 500 | `COMMON-001` | DB 조회 장애 등 예상하지 못한 오류 |

헤더 형식을 먼저 확인하고 DB 조회 후 역할, USER의 멤버십, U01 상태 순서로 검사한다. 여러 조건이 동시에 잘못된 요청은 먼저 발견된 오류를 반환한다. DB에 저장된 null 멤버십을 헤더로 보충하지 않는다.

## Controller에서 사용하기

```java
@GetMapping("/me")
public ResponseEnvelope<UserProfileResponse> getProfile(@CurrentUser User user) {
    return ResponseEnvelope.success(UserProfileResponse.from(userService.getProfile(user)));
}
```

- `@CurrentUser`: `api.common.context`의 파라미터 어노테이션.
- `CurrentUserArgumentResolver`: 어노테이션과 `User` 타입을 함께 확인하고 헤더 해석 → 기존 `UserService.findById(UUID)` → DB 대조를 수행한다.
- `UserContextWebConfig`: `api.common.config`에서 resolver를 MVC에 등록한다.
- `CommonErrorCode`: 사용자 헤더 누락·역할 불일치·USER의 DB 멤버십 누락·멤버십 불일치를 정의한다. 공통으로 옮긴 오류의 HTTP 상태·`USER-003`~`USER-006` 코드·메시지는 기존과 같다. 일반 사용자 조회 실패와 U01의 비활성 제한은 `UserErrorCode`에 둔다.
- `OpenApiConfig`: `@CurrentUser User`를 받는 API에만 사용자 헤더 3개를 Swagger 입력란으로 추가한다. `@CurrentUser`가 User 파라미터를 문서에서 숨기므로 Controller에 헤더별 `@Parameter`를 반복하지 않는다.
- `UserService.getProfile(User)`: U01의 INACTIVE 제한을 검사하며 응답용 DB 조회를 추가하지 않는다.

조회 결과는 해당 HTTP 요청의 속성에만 저장한다. 같은 요청의 다른 `@CurrentUser User` 파라미터도 이를 재사용하고, 다음 요청은 새로 조회한다. 전역 사용자 저장소·필터·인터셉터는 사용하지 않는다.

`@CurrentUser User`가 없는 API에서는 헤더 해석·사용자 조회가 실행되지 않는다. resolver는 INACTIVE 상태도 보존한다. 특정 업무의 역할·상태·자격 제한은 소비 기능의 Service가 담당하며, U01의 제한을 모든 API에 강제하지 않는다. 알림 Service는 검증된 User를 받아 사용자·멤버십을 재조회하지 않고 본인 알림 소유권을 검사한다. 이벤트 등록 Service는 등록 트랜잭션 안에서 기존 관리자·활성 상태 검사를 유지한다.


### 적용된 API와 기존 호출 변경

- `GET /api/v1/users/me`
- `POST /api/v1/admin/events`
- `GET /api/v1/notifications/me`
- `PUT /api/v1/notifications/{notificationId}/read`
- `PUT /api/v1/notifications/me/read-all`

위 API는 모두 같은 헤더 규칙과 공통 `USER-*` 오류 코드를 사용한다. 기존 ID만 보내던 요청에는 `X-User-Role`을 추가하고, USER 요청에는 DB와 일치하는 `X-User-Membership`도 추가해야 한다. 알림의 기존 `USER_CONTEXT_REQUIRED`·`USER_CONTEXT_INVALID`·`USER_MEMBERSHIP_MISMATCH` 응답은 각각 `USER-003`·`USER-002`·`USER-006`으로 통일했다.

알림의 소유권·읽음 처리와 이벤트의 관리자 제한은 유지한다. 알림에서 비활성 사용자를 새로 차단하지 않으며, 일반 사용자는 공통 헤더 대조를 통과해도 관리자 이벤트를 등록할 수 없다.

이 헤더는 시연용 사용자 선택 수단이다. DB 대조는 입력 값의 일치를 확인하며, 호출자가 실제 그 사용자라는 신원 인증을 제공하지 않는다. 적용 환경은 공용 명세의 시연 범위를 따른다.

## 검증

```bash
./gradlew :core:test --tests '*UserServiceTest'
./gradlew :api:test --tests '*UserControllerTest'
./gradlew :api:integrationTest --tests com.getddo.api.user.UserContextIntegrationTest
./gradlew test
./gradlew build
```

Windows에서는 `gradlew.bat`를 사용한다. 통합 테스트와 전체 빌드에는 Docker가 필요하다. 단위 테스트는 헤더 오류·응답 필드·요청당 조회 1회·미적용 API·연속/동시 요청 격리를 확인하고, 통합 테스트는 실제 MVC 등록과 MySQL 조회·응답 연결을 확인한다.
