# GETDDO Backend

출석·미션을 통한 응모권 적립, 이벤트 응모, 관리자 검토·추첨·결과 발표를 위한 백엔드 프로젝트입니다. 현재 기획 및 초기 구조 설정 단계이며, 세부 기능과 정책은 확정되는 대로 반영합니다.

프론트엔드와 백엔드는 별도 Repository로 관리하며, 동일한 브랜치 전략을 따릅니다.

## 기술 구성


| 항목          | 구성                      |
| ----------- | ----------------------- |
| Java        | 21                      |
| Spring Boot | 4.1.1                   |
| Gradle      | Wrapper 사용              |
| 웹           | Spring MVC              |
| 영속성         | Spring Data JPA, Flyway |
| CI          | GitHub Actions          |


## 모듈 및 패키지 구조

하나의 애플리케이션으로 실행하는 멀티모듈 구조입니다. `api`는 실행과 요청·응답, `core`는 업무 규칙, `storage:db`는 DB 구현을 담당합니다.

```text
getddo-be/
├── api/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/getddo/api/
│       │   │   └── GetddoBeApplication.java
│       │   └── resources/application.yaml
│       └── test/
│           ├── java/com/getddo/api/
│           └── resources/
├── core/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/getddo/core/
│       │   └── resources/
│       └── test/
│           ├── java/com/getddo/core/
│           └── resources/
├── storage/
│   └── db/
│       ├── build.gradle
│       └── src/
│           ├── main/
│           │   ├── java/com/getddo/db/
│           │   └── resources/db/migration/
│           └── test/
│               ├── java/com/getddo/db/
│               └── resources/
├── .github/
│   ├── pull_request_template.md
│   └── workflows/build.yml
├── gradle/wrapper/
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

빈 디렉터리는 `.gitkeep`으로 유지합니다. `.gitkeep`은 Git의 특별한 기능이 아니라, 빈 디렉터리를 추적하기 위해 두는 파일입니다. 실제 파일이 추가되면 해당 디렉터리의 `.gitkeep`은 제거해도 됩니다.

### 모듈 의존 관계

```text
api → core
api → storage:db       # 실행 시 구현체 조립
storage:db → core
```

- `api`만 실행 가능한 Spring Boot JAR을 생성합니다.
- `core`와 `storage:db`는 라이브러리 JAR을 생성합니다.
- `core`는 `api`나 `storage:db`를 참조하지 않습니다.
- `storage`는 경로 구분용 프로젝트이며, 실제 DB 코드는 `storage:db`에 둡니다.

### 기능 추가 시 패키지 배치

도메인 패키지는 담당자가 기능 구현 시 생성합니다. 현재는 기본 패키지만 있으며, 아래 구조는 배치 기준입니다.


| 모듈           | 패키지                                 | 배치할 객체                        |
| ------------ | ----------------------------------- | ----------------------------- |
| `api`        | `<도메인>/controller`                  | Controller                    |
| `api`        | `<도메인>/dto/request`, `dto/response` | 요청·응답 DTO                     |
| `core`       | `<도메인>/service`                     | 업무 흐름과 트랜잭션을 담당하는 Service     |
| `core`       | `<도메인>/domain`                      | 상태와 비즈니스 규칙을 가진 도메인 객체        |
| `core`       | `<도메인>/repository`                  | 조회·저장 인터페이스                   |
| `storage:db` | `<도메인>/entity`                      | JPA Entity                    |
| `storage:db` | `<도메인>/repository`                  | JpaRepository, RepositoryImpl |
| `storage:db` | `<도메인>/mapper`                      | Entity와 도메인 객체 변환             |


Controller는 Service를 호출하고, Service는 `core`의 Repository 인터페이스에 의존합니다. `storage:db`가 그 인터페이스를 구현합니다. HTTP DTO와 JPA Entity를 `core`에 직접 전달하지 않습니다.

## 로컬 실행 및 검증

### 터미널

저장소 루트에서 실행합니다. 아래 명령은 DB 설정 등 실행 준비를 마친 뒤 사용합니다.


| 작업        | macOS / Linux            | Windows PowerShell           |
| --------- | ------------------------ | ---------------------------- |
| 애플리케이션 실행 | `./gradlew :api:bootRun` | `.\gradlew.bat :api:bootRun` |
| 전체 빌드·테스트 | `./gradlew build`        | `.\gradlew.bat build`        |
| 전체 테스트    | `./gradlew test`         | `.\gradlew.bat test`         |
| 특정 모듈 테스트 | `./gradlew :core:test`   | `.\gradlew.bat :core:test`   |


## 브랜치 전략

```text
main                 배포·시연 가능한 안정 버전
└── dev              개발 통합 브랜치
    ├── feat/*       기능 개발
    ├── fix/*        버그 수정
    ├── refactor/*   리팩터링
    ├── chore/*      빌드·설정·환경 작업
    └── docs/*       문서 작업
```


| 브랜치    | 운영 규칙                                                          |
| ------ | -------------------------------------------------------------- |
| `main` | 직접 push하지 않습니다. `dev`에서 검증된 코드를 PR로 병합하며, 실행·시연 가능한 상태를 유지합니다. |
| `dev`  | 직접 push하지 않습니다. 작업 브랜치의 변경을 PR로 통합합니다.                         |
| 작업 브랜치 | 작업 하나를 기준으로 `dev`에서 분기하고, 완료 후 `dev`를 대상으로 PR을 생성합니다.          |


### 브랜치 이름

```text
<타입>/<Jira 이슈 키>
```


| 타입         | 용도                         | 예시               |
| ---------- | -------------------------- | ---------------- |
| `feat`     | 새로운 기능                     | `feat/GT-21`     |
| `fix`      | 버그 수정                      | `fix/GT-45`      |
| `refactor` | 기능 변화 없는 코드 개선             | `refactor/GT-53` |
| `chore`    | 빌드, CI, Docker, 설정, 패키지 작업 | `chore/GT-13`    |
| `docs`     | 문서 작업                      | `docs/GT-61`     |


- 타입은 소문자, Jira 프로젝트 키 `GT`는 대문자로 작성합니다.
- 공백이나 작업 설명 접미사를 붙이지 않습니다.
- 브랜치 타입과 커밋 타입은 별도 규칙입니다. 작업 브랜치 안에서 변경에 맞는 커밋 타입을 사용합니다.

Jira의 **GitHub에서 브랜치 만들기** 기능 사용을 권장합니다.

```text
Repository: 작업할 프론트엔드 또는 백엔드 Repository
Branch from: dev
Branch name: feat/GT-21
```

### 기본 개발 흐름

```text
Jira 이슈 생성
→ dev 기준 작업 브랜치 생성
→ 로컬 작업 및 검증
→ Commit / Push
→ dev 대상 PR 생성
→ CI Build / Test 성공
→ 코드 리뷰 및 1명 이상 Approve
→ Squash and Merge
→ dev 통합
```

`dev`에서 통합 검증을 마친 변경은 별도 PR을 통해 `main`에 반영합니다.

## 커밋 메시지

```text
type: 작업 내용
```


| 타입         | 용도                    |
| ---------- | --------------------- |
| `feat`     | 새로운 기능 추가             |
| `fix`      | 버그 수정                 |
| `docs`     | 코드 변경 없는 문서 수정        |
| `style`    | 논리 변경 없는 포맷팅 등 스타일 수정 |
| `refactor` | 기능 변화 없는 코드 개선        |
| `test`     | 테스트 추가·수정             |
| `chore`    | 빌드, 패키지, 설정 등 기타 작업   |
| `design`   | CSS 등 사용자 UI 디자인 변경   |
| `comment`  | 필요한 주석 추가·변경          |
| `rename`   | 파일·폴더 이름 변경 또는 이동만 수행 |
| `remove`   | 파일 삭제만 수행             |
| `!HOTFIX`  | 긴급한 치명적 버그 수정         |


예시:

```text
chore: 멀티모듈 초기 구조 설정
test: 응모권 차감 동시성 테스트 추가
docs: 로컬 실행 방법 정리
```

`!HOTFIX` 타입도 PR·CI·리뷰 절차를 생략하는 의미는 아닙니다.

## Pull Request

[PR 템플릿](.github/pull_request_template.md)에 따라 다음을 작성합니다.


| 항목       | 작성 내용                                      |
| -------- | ------------------------------------------ |
| Jira 티켓  | 관련 이슈 키 또는 링크. 예: `GT-13`                  |
| 변경 내용    | 무엇을 왜 변경했는지                                |
| 테스트      | 실행한 검증과 결과. 실행하지 않았다면 이유                   |
| 리뷰 참고 사항 | 중점 검토 부분, DB·환경변수 등 적용 시 필요한 사항. 없으면 생략 가능 |


체크리스트:

- [ ] 변경 내용을 직접 확인했습니다.
- [ ] 테스트 결과 또는 미실행 사유를 작성했습니다.
- [ ] 관련 문서와 설정 변경 사항을 반영했습니다. (해당하는 경우)

작업 브랜치에서 `dev`로 병합할 때는 CI 성공과 리뷰어 1명 이상의 승인을 확인한 뒤 **Squash and Merge**를 사용합니다. Squash 커밋 메시지도 커밋 메시지 형식에 맞춥니다.