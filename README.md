# GETDDO Backend

## 목차

1. [프로젝트 소개](#프로젝트-소개)
2. [개발 방식](#개발-방식)
3. [핵심 기능](#핵심-기능)
4. [기술 스택](#기술-스택)
5. [아키텍처](#아키텍처)
6. [ERD](#erd)
7. [패키지 구조](#패키지-구조)
8. [실행 방법](#실행-방법)

## 프로젝트 소개

GETDDO는 경품 이벤트를 소재로 한 **이벤트 응모 및 추첨 플랫폼**입니다. 출석·미션·게임으로 응모권을 모으고, 이벤트에 응모한 뒤 추첨 결과를 확인하는 흐름을 제공합니다.

이 저장소는 GETDDO의 백엔드입니다. 응모권 잔액과 이력의 정합성, 중복 요청 방지, 추첨 후보·조건·결과 보존을 중심으로 개발하고 있습니다. 가상 사용자와 더미 데이터로 시연하며, 외부 알림 발송은 모의 처리합니다.

프로젝트의 목표와 범위는 [공용 요구사항](https://github.com/GETDDO/getddo-spec/blob/main/00-requirements/README.md)을 기준으로 합니다.

## 개발 방식

프론트엔드와 백엔드는 별도 저장소에서 개발하고, 공용 요구사항·도메인 정책·협업 규칙은 [getddo-spec](https://github.com/GETDDO/getddo-spec)에서 관리합니다. 백엔드의 기능별 담당 영역과 공동 작업 경계는 [백엔드 기능 담당자](docs/01-conventions/backend-feature-assignments.md)를 따릅니다.

개발은 Jira 이슈 생성 → `dev` 기준 작업 브랜치 → 구현·테스트 → Pull Request → CI·코드 리뷰 → 병합 순서로 진행합니다. 상세 규칙은 다음 원본 문서를 확인합니다.

- [브랜치 전략](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/branch.md)
- [커밋 규칙](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/commit.md)
- [개발 흐름](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/workflow.md)
- [Pull Request 규칙](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/pull-request.md)
- [백엔드 코드 스타일](docs/01-conventions/code-style.md)
- [백엔드 문서와 작업 기록](docs/README.md)
- [이 저장소의 PR 템플릿](.github/pull_request_template.md)

## 핵심 기능

아래는 개발 중인 서비스의 주요 기능 범위입니다. 세부 정책과 구현·제외 범위는 [기능 요구사항](https://github.com/GETDDO/getddo-spec/blob/main/00-requirements/functional-requirements.md), [도메인 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/README.md), [구현 범위](https://github.com/GETDDO/getddo-spec/blob/main/00-requirements/scope.md)를 확인합니다.

| 기능 | 내용 |
| --- | --- |
| 출석·미션·게임 | 일일·연속 출석, 퀴즈·설문, 게임 수행에 따른 응모권 보상 |
| 응모권 관리 | 응모권 잔액과 지급·차감·반환·회수·만료 이력 관리 |
| 이벤트·응모 | 이벤트와 경품 조회, 응모 조건 검증, 응모 접수와 내 응모 내역 조회 |
| 참여 검토·추첨 | 어뷰징 의심 건 검토, 추첨 후보·조건 보존, 가중치 추첨과 결과 발표 |
| 백오피스 | 이벤트·경품·배너·보상 정책 운영, 사용자·응모·감사 로그 조회 |
| 알림 | 이벤트와 처리 결과에 대한 사이트 내 알림, 읽음 상태 관리 |

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1, Spring MVC |
| 빌드 | Gradle 9.7.1 · Wrapper 사용 |
| 데이터베이스 | MySQL 8.4 |
| 영속성·마이그레이션 | Spring Data JPA, Flyway |
| 객체 매핑 | MapStruct 1.6.3, Lombok |
| API 문서 | springdoc-openapi 3.1.1 · Swagger UI |
| 테스트 | JUnit, Spring Boot Test, Testcontainers |
| 실행 환경 | Docker, Docker Compose |
| CI | GitHub Actions |

## 아키텍처

하나의 Spring Boot 애플리케이션으로 실행하는 Gradle 멀티모듈 구조입니다. `api`는 실행과 요청·응답, `core`는 업무 규칙, `storage:db`는 DB 구현을 담당합니다.

### 모듈 의존 관계

```mermaid
flowchart LR
    api["api · Controller / DTO"] --> core["core · Service / Domain / Repository 인터페이스"]
    api --> storage["storage:db · JPA / Repository 구현"]
    storage --> core
```

- `api`는 `core`와 `storage:db`를 조립하며, 실행 가능한 Spring Boot JAR을 생성합니다.
- `core`와 `storage:db`는 라이브러리 JAR을 생성합니다.
- `core`는 `api`나 `storage:db`를 참조하지 않습니다.
- `storage`는 경로 구분용 프로젝트이며, 실제 DB 코드는 `storage:db`에 둡니다.

Controller는 Service를 호출하고, Service는 `core`의 Repository 인터페이스에 의존합니다. `storage:db`가 그 인터페이스를 구현해 MySQL에 접근합니다. HTTP DTO와 JPA Entity를 `core`에 직접 전달하지 않습니다.

로컬에서는 Docker Compose로 애플리케이션과 MySQL을 실행합니다. 앱 시작 시 Flyway가 마이그레이션을 적용합니다.

## ERD

전체 스키마는 41개 테이블로 구성됩니다. 아래는 사용자·응모권·이벤트·추첨의 주요 관계를 추린 요약이며, 전체 컬럼과 관계는 [통합 스키마 DBML](docs/03-database/schema.dbml)을 확인합니다.

```mermaid
erDiagram
    users ||--o{ ticket_wallets : "응모권 지갑"
    ticket_wallets ||--o{ ticket_ledger : "거래 이력"
    users ||--o{ event_participants : "이벤트 응모자"
    events ||--o{ event_participants : "응모자"
    events ||--o{ event_entries : "응모 요청"
    event_participants |o--o{ event_entries : "접수 완료 시 연결"
    events ||--o{ event_prizes : "경품"
    events ||--o{ draw_runs : "추첨 실행"
    draw_runs ||--o{ draw_candidates : "후보 스냅샷"
    event_participants ||--o{ draw_candidates : "추첨 후보"
    draw_runs ||--o{ draw_results : "추첨 결과"
    event_prizes ||--o{ draw_results : "경품별 결과"
```

DBML은 ERD 확인용이며, 실제 DB 적용 기준은 [Flyway 마이그레이션](storage/db/src/main/resources/db/migration)입니다. 설계 설명과 검토 문서는 [DB 문서](docs/03-database/README.md)에서 관리합니다.

## 패키지 구조

모듈별로 도메인 패키지를 나누고, 각 도메인 안에 Controller·Service·Repository 등 역할별 패키지를 둡니다.

```text
getddo-be/
├── api/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/getddo/api/
│       │   │   └── GetddoBeApplication.java
│       │   └── resources/application.yaml
│       ├── integrationTest/java/com/getddo/api/
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
│           ├── integrationTest/java/com/getddo/db/
│           ├── testFixtures/java/com/getddo/db/support/
│           └── test/
│               ├── java/com/getddo/db/
│               └── resources/
├── .github/
│   ├── pull_request_template.md
│   └── workflows/build.yml
├── docs/
├── gradle/wrapper/
├── compose.yaml
├── Dockerfile
├── .env.example
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

빈 디렉터리는 `.gitkeep`으로 유지합니다. `.gitkeep`은 Git의 특별한 기능이 아니라, 빈 디렉터리를 추적하기 위해 두는 파일입니다. 실제 파일이 추가되면 해당 디렉터리의 `.gitkeep`은 제거해도 됩니다.

### 기능 추가 시 패키지 배치

도메인 패키지는 담당자가 기능 구현 시 생성하며, 아래 기준으로 객체를 배치합니다.


| 모듈           | 패키지                                 | 배치할 객체                        |
| ------------ | ----------------------------------- | ----------------------------- |
| `api`        | `<도메인>/controller`                  | Controller                    |
| `api`        | `<도메인>/dto/request`, `dto/response` | 요청·응답 DTO                     |
| `api`        | `common/context`                    | 여러 API에서 사용하는 사용자 문맥 어노테이션·ArgumentResolver |
| `api`        | `common/config`                     | MVC·OpenAPI 등 API 공통 설정       |
| `core`       | `<도메인>/service`                     | 업무 흐름과 트랜잭션을 담당하는 Service     |
| `core`       | `<도메인>/domain`                      | 상태와 비즈니스 규칙을 가진 도메인 객체        |
| `core`       | `<도메인>/repository`                  | 조회·저장 인터페이스                   |
| `core`       | `<도메인>/exception`                   | 도메인 오류 코드(`ErrorCode` 구현 enum)   |
| `storage:db` | `<도메인>/entity`                      | JPA Entity                    |
| `storage:db` | `<도메인>/repository`                  | JpaRepository, RepositoryImpl |
| `storage:db` | `<도메인>/mapper`                      | Entity와 도메인 객체 변환             |


## 실행 방법

Docker Compose 실행에는 Docker가 필요합니다. 터미널에서 애플리케이션이나 Gradle 작업을 실행하려면 JDK 21도 준비합니다. Gradle은 저장소의 Wrapper를 사용합니다.

### Docker Compose

저장소 루트에서 `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`에 각각 로컬 비밀번호를 입력합니다. DB 이름과 일반 계정은 예시의 `getddo`를 사용합니다. `.env`는 Git과 Docker 빌드 컨텍스트에서 제외됩니다.

```bash
docker compose up --build -d
```

앱은 `http://localhost:8080`, MySQL 8.4는 `localhost:3306`에서 접근할 수 있습니다. Compose는 `local` Spring 프로필을 활성화하고, `application-local.yaml`에 정의된 MySQL JDBC 연결에 DB 환경변수를 전달합니다. MySQL 데이터는 Compose 볼륨에 유지됩니다. 종료할 때는 `docker compose down`을 사용합니다.

앱 시작 시 Flyway가 `storage:db`의 도메인별 SQL V001~V011을 버전 순서대로 적용해 41개 테이블을 생성합니다. 이벤트와 배너의 논리 삭제 시각, 출석 기준일·사용자별 하루 1회 UNIQUE와 외래 키는 각 테이블의 초기 생성 정의에 포함되어 있습니다. Docker Desktop을 WSL에서 사용하는 경우 WSL 연동을 활성화해야 합니다.

### 터미널

JDK 21을 준비하고 저장소 루트에서 실행합니다. 앱을 터미널에서 실행하려면 앞의 `.env` 준비 후 `docker compose up -d db`로 개발용 MySQL만 시작합니다. `bootRun`은 `.env`를 자동으로 읽지 않으므로 같은 DB 접속 정보를 환경변수로 지정합니다.

macOS / Linux / WSL:

```bash
export SPRING_PROFILES_ACTIVE=local
export DB_HOST=localhost DB_PORT=3306
export DB_NAME=getddo DB_USERNAME=getddo
export DB_PASSWORD='<.env에 설정한 DB_PASSWORD 값>'
./gradlew :api:bootRun
```

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'getddo'
$env:DB_USERNAME = 'getddo'
$env:DB_PASSWORD = '<.env에 설정한 DB_PASSWORD 값>'
.\gradlew.bat :api:bootRun
```

DB 이름이나 계정을 바꿨다면 환경변수도 `.env`와 맞춥니다.

### 테스트와 CI

DB가 필요한 테스트는 Testcontainers가 실행하는 임시 MySQL 8.4를 사용합니다. 개발용 DB·`.env` 설정 없이 실행하며, 임의의 호스트 포트로 연결하고 테스트 종료 시 컨테이너를 정리합니다. H2는 사용하지 않습니다.

공통 설정은 `storage/db/src/testFixtures/java/com/getddo/db/support`에 있습니다. `MySqlTestContainers`는 호출마다 새 컨테이너를 만들고, `MySqlTestConfiguration`은 `@ServiceConnection`으로 Spring의 JDBC·Flyway 연결을 자동 설정합니다. Spring 테스트는 `test` 프로필을 사용하며 컨테이너 시작·종료는 Spring이 관리합니다. 이 설정은 통합 테스트에서만 사용하고 운영 실행 JAR에는 포함하지 않습니다.

API의 앱 기동·Swagger 테스트는 `api/src/integrationTest/java/com/getddo/api/support/ApiIntegrationTest.java`의 `@ApiIntegrationTest`를 사용해 동일한 Spring 컨텍스트와 MySQL 하나를 공유합니다. 공통 Entity 테스트는 테이블을 생성·삭제하는 별도 DB를 사용하고, 초기 SQL 테스트는 JUnit이 관리하는 별도 컨테이너의 빈 DB에서 실행합니다. 다른 모듈이나 서로 다른 Spring 컨텍스트까지 강제로 공유하지 않습니다.

통합 테스트 전에 Docker Desktop의 Linux 컨테이너 엔진을 실행합니다. WSL에서는 Settings → Resources → WSL Integration에서 사용하는 배포판을 켜고, `docker version`에 Client와 Server가 모두 표시되는지 확인합니다. 첫 실행에는 MySQL 이미지 다운로드가 필요합니다.

| 작업 | macOS / Linux / WSL | Windows PowerShell | Docker |
| --- | --- | --- | --- |
| DB 없는 테스트 | `./gradlew test` | `.\gradlew.bat test` | 불필요 |
| MySQL 통합 테스트 | `./gradlew integrationTest` | `.\gradlew.bat integrationTest` | 필요 |
| 초기 SQL·공통 Entity 검증 | `./gradlew :storage:db:integrationTest` | `.\gradlew.bat :storage:db:integrationTest` | 필요 |
| 앱 기동·Swagger 검증 | `./gradlew :api:integrationTest` | `.\gradlew.bat :api:integrationTest` | 필요 |
| 전체 빌드·테스트 | `./gradlew build` | `.\gradlew.bat build` | 필요 |
| 실행 JAR 패키징 | `./gradlew :api:bootJar` | `.\gradlew.bat :api:bootJar` | 불필요 |

초기 SQL 검증은 빈 MySQL에 Flyway 마이그레이션을 적용하고, 재실행 시 추가 적용이 없는지 확인합니다. Docker에 연결할 수 없으면 통합 테스트는 실패합니다.

GitHub Actions의 `빌드·테스트`는 러너의 Docker에서 같은 `./gradlew build`를 실행합니다. 별도 DB 비밀값 설정은 필요하지 않습니다. 실패한 테스트 보고서는 `test-reports` 아티팩트에서 확인할 수 있습니다.

`PR 제목 검사`는 [공용 PR 제목 규칙](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/pull-request.md)에 맞는지 별도로 확인합니다. PR 생성·수정·커밋 추가 시 실행하며, 제목·본문만 수정하면 Gradle 빌드는 실행하지 않습니다. 제목 오류로 병합을 막으려면 main/dev Ruleset의 필수 상태 검사에 `PR 제목 검사`를 등록합니다.
