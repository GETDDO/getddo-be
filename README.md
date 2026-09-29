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

### Docker Compose

저장소 루트에서 `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`에 각각 로컬 비밀번호를 입력합니다. DB 이름과 일반 계정은 예시의 `getddo`를 사용합니다. `.env`는 Git과 Docker 빌드 컨텍스트에서 제외됩니다.

```bash
docker compose up --build -d
```

앱은 `http://localhost:8080`, MySQL 8.4는 `localhost:3306`에서 접근할 수 있습니다. Compose는 `local` Spring 프로필을 활성화하고, `application-local.yaml`에 정의된 MySQL JDBC 연결에 DB 환경변수를 전달합니다. MySQL 데이터는 Compose 볼륨에 유지됩니다. 종료할 때는 `docker compose down`을 사용합니다.

앱 시작 시 Flyway가 `storage:db`의 도메인별 SQL V001~V012를 버전 순서대로 적용합니다. V001~V011은 41개 테이블을 생성하고, V012는 이벤트와 배너에 논리 삭제 시각을 추가합니다. 출석 기준일·사용자별 하루 1회 UNIQUE와 외래 키는 각 테이블의 초기 생성 정의에 포함되어 있습니다. Docker Desktop을 WSL에서 사용하는 경우 WSL 연동을 활성화해야 합니다.

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
