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


## 문서와 협업 규칙

공용 요구사항, 도메인 정책 및 협업 규칙은 [getddo-spec](https://github.com/GETDDO/getddo-spec)에서 관리합니다. 이 저장소에 공용 문서를 복사하지 않고 원본을 참조합니다.

- [기능 요구사항](https://github.com/GETDDO/getddo-spec/blob/main/00-requirements/functional-requirements.md)
- [도메인 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/README.md)
- [브랜치 전략](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/branch.md)
- [커밋 규칙](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/commit.md)
- [개발 흐름](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/workflow.md)
- [Pull Request 규칙](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/pull-request.md)
- [백엔드 문서와 작업 기록](docs/README.md)
- [백엔드 코드 스타일](docs/01-conventions/code-style.md)
- [이 저장소의 PR 템플릿](.github/pull_request_template.md)
