# GETDDO Backend

GETDDO는 응모권을 모아 경품 이벤트에 참여하고 추첨 결과를 확인하는 **이벤트 응모 및 추첨 플랫폼**입니다.

### 목차

1. [기술 스택](#기술-스택)
2. [프로젝트 개요](#프로젝트-개요)
3. [프로젝트 팀 구성 및 역할](#프로젝트-팀-구성-및-역할)
4. [아키텍처와 패키지 구조](#아키텍처와-패키지-구조)
5. [ERD](#erd)
6. [프로젝트 수행 경과](#프로젝트-수행-경과)
7. [실행 방법](#실행-방법)

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 | ![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) |
| 프레임워크 | ![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) ![Spring MVC](https://img.shields.io/badge/Spring_MVC-6DB33F?style=for-the-badge&logo=spring&logoColor=white) |
| 빌드 | ![Gradle 9.7.1](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white) |
| 데이터베이스 | ![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white) |
| 캐시 · 도입 예정 | ![Redis](https://img.shields.io/badge/Redis-FF4438?style=for-the-badge&logo=redis&logoColor=white) |
| 영속성·마이그레이션 | ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white) |
| 객체 매핑 | ![MapStruct 1.6.3](https://img.shields.io/badge/MapStruct-1.6.3-455A64?style=for-the-badge) ![Lombok](https://img.shields.io/badge/Lombok-BC4521?style=for-the-badge) |
| API 문서 | ![springdoc-openapi 3.1.1](https://img.shields.io/badge/springdoc--openapi-3.1.1-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) ![Swagger UI](https://img.shields.io/badge/Swagger_UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) |
| 테스트 | ![JUnit](https://img.shields.io/badge/JUnit-25A162?style=for-the-badge&logo=junit5&logoColor=white) ![Spring Boot Test](https://img.shields.io/badge/Spring_Boot_Test-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-2496ED?style=for-the-badge) |
| 실행 환경 | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white) ![Docker Compose](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white) |
| CI | ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white) |

## 프로젝트 개요

### 서비스 소개와 기획 배경

커머스의 경품 이벤트는 사용자의 참여를 유도하는 동시에 응모권 지급·사용 이력과 추첨 결과에 대한 신뢰가 필요한 기능입니다. GETDDO는 이 과제를 주제로 출석·미션·게임에서 이벤트 참여와 운영자용 백오피스까지 이어지는 서비스를 개발합니다.

사용자가 보는 참여 화면뿐 아니라 중복 요청, 동시 응모, 응모권 반환, 추첨 근거 보존과 실패 복구를 함께 다룹니다. 기획의 기준은 [공용 요구사항](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/00-requirements/README.md)입니다.

### 프로젝트 목표

| 목표 | 확인할 내용 |
| --- | --- |
| 참여형 이벤트 경험 | 활동으로 응모권을 얻고 이벤트 조회·응모·결과 확인으로 이어지는 사용자 흐름 |
| 응모권의 일관성 | 지급·차감·반환·만료 이력과 현재 응모권 상태의 일치 |
| 신뢰할 수 있는 응모·추첨 | 중복 접수·중복 당첨 방지와 후보·가중치·조건·확정 결과 보존 |
| 운영 가능한 서비스 | 관리자 이벤트·경품 관리, 취소 처리, 알림과 실패 재처리 |

### 주요 서비스 흐름

```mermaid
flowchart LR
    Earn["출석·미션·게임으로 응모권 획득"] --> Browse["이벤트 조회"]
    Browse --> Enter["이벤트 응모·응모권 사용"]
    Enter --> Close["모집 마감"]
    Close --> Draw["마감 5분 후 자동 추첨"]
    Draw --> Review["관리자 확인·수동 공개"]
    Review --> Result["사용자 결과 확인·알림"]
```

이 그림은 목표 서비스 흐름입니다. 기능별 구현·검증·연동 현황은 [프로젝트 수행 경과](#프로젝트-수행-경과)에 각 도메인 담당자가 작성합니다. 응모권 미사용 이벤트도 있으며, 모든 이벤트가 응모권을 차감하는 것은 아닙니다. 추첨 실행과 사용자에게 결과를 공개하는 시점은 분리합니다.

## 프로젝트 팀 구성 및 역할

### 팀원 소개와 담당 영역

| 팀원 | 담당 영역 | 주요 기능 |
| --- | --- | --- |
| 이경주 | 인프라·공통 기반·사용자 | 실행·배포 환경, 공통 응답·오류·시간·감사 기록, 사용자·역할·멤버십 |
| 석종수 | 어뷰징 차단 기록·추첨 | 지급 전 차단 근거 기록, 후보·가중치 스냅샷, 추첨·당첨 취소·재추첨·결과 공개 |
| 전민규 | 백오피스·알림·게임 | 관리자 운영 기능, 배너·정책 관리, 알림 생성·발송·복구, 게임 검증·보상 자격 |
| 윤태형 | 응모권·미션·출석·응모 | 응모권 지급·차감·반환·만료, 출석·미션 보상, 응모 접수·내역 |
| 박지훈 | 이벤트·경품 관리·이벤트 취소 | 사용자·관리자 이벤트 조회, 이벤트·경품 CRUD, 시작·마감 전환, 취소 연동 |

담당 영역은 구현 책임을 나타내며 완료 현황을 의미하지 않습니다. 상세 경계는 [백엔드 기능 담당자](docs/01-conventions/backend-feature-assignments.md)를 확인합니다.

## 아키텍처와 패키지 구조

### 전체 아키텍처

단일 Spring Boot 애플리케이션으로 실행하는 멀티모듈 구조이며, 각 모듈 안을 기능별 패키지로 나눕니다.

```mermaid
flowchart LR
    Client["사용자·관리자 프론트"] --> API["api: HTTP 요청·응답"]
    API --> Core["core: 업무 규칙·Service·Repository 계약"]
    API --> DB["storage:db: JPA·JDBC 구현"]
    DB --> Core
    DB --> MySQL[("MySQL 8.4")]
    Worker["같은 앱의 예약 워커"] --> Core
```

### 클라우드 설계도

[![GETDDO Vercel·AWS 전체 아키텍처](docs/assets/cloud-architecture.png)](docs/assets/cloud-architecture.drawio)

[draw.io 원본](docs/assets/cloud-architecture.drawio). Vercel이 프론트 화면 파일을 전달하고, 브라우저가 CloudFront를 통해 API와 이미지를 요청하는 구성입니다. EC2 2대와 공유 RDS·Redis를 포함한 확장 설계이며, 실제 두 서버 운영과 앱 연동 완료를 뜻하지 않습니다. 실제 배포 검증 상태는 [배포 문서](docs/06-runbook/container-cd.md)를 확인합니다.

### 모듈 의존 관계

api → core, api → storage:db, storage:db → core는 빌드 의존 방향입니다. 실제 업무 서비스는 Repository 인터페이스를 통해 주입된 저장 구현을 호출합니다. 모듈을 별도 서버로 배포하는 구조는 아닙니다.

| 구분 | 역할·사용 이유 |
| --- | --- |
| api | HTTP DTO·형식 검증·공통 사용자 문맥·응답 변환을 업무 규칙과 분리 |
| core | API와 워커가 업무 규칙·트랜잭션을 재사용. Spring Context/Transaction 의존성은 허용 |
| storage:db | Entity·JPA/JDBC·잠금·쿼리·마이그레이션을 저장 책임에 모음 |

이벤트 시작·마감과 응모권 만료는 각각 전용 스레드를 사용합니다. 알림·추첨은 DB 작업 상태·소유권 만료·재시도로 중복 처리와 장애 복구를 관리합니다. [Wiki 전체 아키텍처](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Architecture)에 호출 흐름·트랜잭션과 워커 경계를 설명했습니다.

### 패키지 구조

하나의 Spring Boot 애플리케이션으로 실행하는 멀티모듈 구조입니다. 아래는 주요 디렉터리와 기능 추가 시 패키지 배치 기준입니다. `<도메인>`에는 `user`, `event`, `ticket` 등 기능별 패키지 이름이 들어갑니다.

```text
getddo-be/
├── api/                                         # HTTP 요청·응답과 애플리케이션 실행
│   ├── build.gradle                             # 웹·검증·API 문서 의존성
│   └── src/
│       ├── main/
│       │   ├── java/com/getddo/api/
│       │   │   ├── GetddoBeApplication.java     # Spring Boot 진입점
│       │   │   ├── common/                      # API 공통 처리
│       │   │   │   ├── config/                  # MVC·CORS·OpenAPI 설정
│       │   │   │   ├── context/                 # 사용자 문맥 어노테이션·ArgumentResolver
│       │   │   │   ├── exception/               # 공통 API 오류 코드·전역 예외 처리
│       │   │   │   └── response/                # 공통 응답 형식 ResponseEnvelope
│       │   │   └── <도메인>/
│       │   │       ├── controller/              # 요청을 받아 Service에 위임
│       │   │       ├── dto/request/             # 요청 DTO와 입력 검증
│       │   │       └── dto/response/            # 응답 DTO·from(domain) 변환
│       │   └── resources/                       # application.yaml 등 실행 설정
│       ├── test/                                # DB 없는 API·설정 테스트
│       └── integrationTest/                     # 앱 기동·API·DB 연동 테스트
├── core/                                        # 도메인 모델과 비즈니스 규칙
│   ├── build.gradle                             # Spring Context·트랜잭션 의존성
│   └── src/
│       ├── main/java/com/getddo/core/
│       │   ├── common/                          # 공통 예외·페이지 조회 모델·시각 처리
│       │   └── <도메인>/
│       │       ├── domain/                      # 도메인 객체·상태·비즈니스 규칙
│       │       ├── service/                     # 업무 흐름과 트랜잭션
│       │       ├── repository/                  # 조회·저장 인터페이스
│       │       └── exception/                   # 도메인 오류 코드(ErrorCode 구현)
│       └── test/                                # 도메인 규칙·Service 테스트
├── storage/                                     # 영속성 모듈의 경로 구분
│   └── db/                                      # MySQL 영속성 구현
│       ├── build.gradle                         # JPA·Flyway·MySQL 드라이버 의존성
│       └── src/
│           ├── main/
│           │   ├── java/com/getddo/db/
│           │   │   ├── common/                  # 공통 Entity·JPA Auditing 설정
│           │   │   └── <도메인>/
│           │   │       ├── entity/              # DB 테이블에 대응하는 JPA Entity
│           │   │       ├── repository/          # JpaRepository·core Repository 구현
│           │   │       └── mapper/              # Entity ↔ 도메인 객체 변환
│           │   └── resources/db/migration/      # 도메인별 Flyway SQL
│           ├── test/                            # DB 없는 영속성 관련 테스트
│           ├── integrationTest/                 # 실제 MySQL·마이그레이션 검증
│           └── testFixtures/                    # 공유 Testcontainers 설정
├── .github/                                     # GitHub 협업·자동화 설정
│   ├── pull_request_template.md                 # PR 작성 양식
│   └── workflows/                               # 빌드·테스트·PR 제목 검사
├── docs/                                        # 백엔드 API·DB·결정·작업 기록
├── gradle/wrapper/                              # Gradle 실행 버전 고정
├── compose.yaml                                 # 로컬 앱·MySQL 컨테이너 구성
├── Dockerfile                                   # 애플리케이션 이미지 빌드
├── .env.example                                 # 로컬 환경변수 예시
├── build.gradle                                 # 모듈 공통 빌드·테스트 설정
├── settings.gradle                              # 프로젝트 이름·모듈 등록
├── gradlew                                      # macOS·Linux·WSL Gradle 실행
└── gradlew.bat                                  # Windows Gradle 실행
```

### 기능 추가 시 패키지 배치

새 기능의 HTTP DTO·Controller는 `api`, 업무 서비스·모델·Repository 계약은 `core`, Entity·저장 구현·Mapper는 `storage:db`의 같은 기능 패키지에 둡니다. 도메인 오류 코드는 `core/<도메인>/exception`에 배치하고 기존 공통 응답·페이지·시간 기능을 재사용합니다.

의존 방향은 `api → core`, `api → storage:db`, `storage:db → core`입니다. `api`만 실행 가능한 Spring Boot JAR을 만들고, 나머지 두 모듈은 라이브러리 JAR을 만듭니다. `core`는 API DTO나 JPA Entity에 의존하지 않습니다.

## ERD

아래 이미지는 2026-10-16에 제공된 ERDCloud 전체 캡처입니다. 이미지를 누르면 원본 크기로 확인할 수 있습니다. 실제 적용 스키마는 전체 Flyway 적용 결과를 기준으로 확인합니다.

[![GETDDO 전체 ERD — 2026-10-16](docs/assets/erd-2026-10-16.png)](docs/assets/erd-2026-10-16.png)

[ERDCloud에서 확대·이동하며 보기](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9) · [원본 이미지](docs/assets/erd-2026-10-16.png)

### 도메인별 ERD

도메인을 선택하면 GitHub Wiki에서 해당 도메인의 전체 ERD와 테이블별 컬럼 표를 확인할 수 있습니다. 이미지를 누르면 원본 크기로 열립니다. [전체 Wiki 목록](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD)에서도 도메인별로 이동할 수 있습니다.

도메인별 Wiki와 아래 테이블 수는 기존 2026-10-02 스냅샷 기준으로, 위 전체 ERD 캡처와 기준 시점이 다릅니다.

| 도메인 | 테이블 수 | 내용 |
| --- | ---: | --- |
| [사용자](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-user) | 1 | 사용자 정보·역할·멤버십 |
| [이벤트·응모](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-event) | 5 | 이벤트·경품·응모자·응모 요청·배너 |
| [출석](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-attendance) | 5 | 일일·연속 출석과 보상 기록 |
| [미션](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-mission) | 10 | 퀴즈·설문·제출·보상 기록 |
| [게임](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-game) | 4 | 게임·플레이·통계·보상 기록 |
| [보상 정책](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-reward) | 1 | 출석·미션·게임의 보상 정책 |
| [응모권](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-ticket) | 2 | 응모권 한 장의 현재 상태와 처리 이력 |
| [어뷰징 검토](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-abuse) | 1 | 의심 행위 탐지와 관리자 검토 |
| [추첨·발표](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-drawing) | 6 | 추첨 실행·후보·결과·당첨·발표 |
| [알림](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-notification) | 2 | 알림 생성 작업과 사용자 알림 |
| [감사 로그](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-audit) | 1 | 변경 행위와 변경 전후 데이터 |

## 프로젝트 수행 경과

각 도메인 담당자가 아래 항목을 펼쳐 직접 작성합니다. 구현 기능, 설계·기술 선택의 이유, 문제 해결 과정, 실제 테스트 결과와 남은 작업을 정리합니다. **코드 구현·배포·프론트 연동 상태를 구분**하고 검증 결과에는 실행일과 기준 커밋을 함께 적습니다.

<!-- 아래 작성 틀을 담당자의 실제 수행 내용으로 교체한다. 다른 담당자의 구현·완료 상태를 대신 확정하지 않는다. -->

<details>
<summary>인프라·공통 기반</summary>

<!-- 작성 담당: 이경주 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

기존 참고 자료: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Architecture) — 해당 문서의 기준 시점과 현재 구현을 확인한 뒤 작성합니다.

</details>

<details>
<summary>사용자</summary>

<!-- 작성 담당: 이경주 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

기존 참고 자료: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#user) — 해당 문서의 기준 시점과 현재 구현을 확인한 뒤 작성합니다.

</details>

<details>
<summary>이벤트·경품 관리·이벤트 취소</summary>

<!-- 작성 담당: 박지훈 -->

> 작성 초안 — 담당자 확인 전. 구현 설명은 dev `94ce322b`, 테스트 기록은 아래 별도 기준 커밋을 따릅니다.

**구현 기능·시연 흐름**

- 사용자·관리자 이벤트 목록·상세 조회, 상태·유형 필터와 페이지 조회를 제공합니다. 관리자 검색은 KST 날짜 범위와 모집 기간이 겹치는 이벤트를 조회합니다.
- 이벤트 등록 시 경품을 함께 저장하고, 등수별 경품 종류·당첨 인원·멤버십·응모 조건을 검증합니다. 이벤트·경품 이미지 키는 선택값입니다.
- 시작 전 진행 예정 이벤트의 설정과 경품을 수정합니다. 시작 시각 앞당김을 막고 기존 경품 ID를 유지합니다.
- 시작 전이며 응모 이력이 없는 이벤트는 논리 삭제합니다. 배너는 관리자가 별도로 삭제합니다.
- 이벤트 취소 시 취소 상태·응모권 반환·취소 알림 작업 등록·감사 기록을 연결합니다. 이미 취소된 이벤트의 재요청은 409로 거절합니다.
- 전용 워커가 시작·마감 시각에 모집 상태를 전환합니다. 관리자 응답의 drawScheduledAt은 마감 +5분 자동 추첨 기준이며 실제 결과 공개 시각과 구분합니다.

**설계·기술 선택과 사용 이유**

- 조회는 JDBC로 검색·공개 상태 조건을 표현하고, 변경은 JPA와 이벤트 행 잠금으로 관리합니다.
- 이벤트·경품 저장과 감사 기록, 취소·반환은 업무 트랜잭션으로 묶어 일부만 완료되는 상황을 막습니다.
- 상태 전환은 이벤트별 트랜잭션과 SKIP LOCKED를 사용해 워커 경합과 개별 실패를 분리합니다.

**문제와 해결 과정**

- 잠금 대기 중 시작 시각이 지날 수 있어 잠금 획득 후 시각·상태를 다시 검사하도록 보완했습니다.
- 경품 등수 교환 시 UNIQUE 충돌을 피하도록 같은 트랜잭션에서 임시 등수를 거쳐 변경하고 기존 경품 ID를 보존했습니다.
- 한 이벤트의 감사 저장 실패가 후속 이벤트 전환을 막지 않도록 이벤트별 롤백과 순환 조회를 적용했습니다.

**테스트 결과**

- 2026-10-16 KST, dev `15f2d95c`의 전체 `test build --continue --offline --no-daemon --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel` 실행에서 event 패키지 **255개**(단위·MVC·설정 131 / MySQL 통합 124)가 성공했습니다. 실패·오류·건너뜀은 0개입니다.
- 등록·수정·삭제·취소 조건, 잠금 경합, 감사·저장 실패 롤백과 워커 상태 전환을 검증했습니다. 이번 README 재구성에서는 테스트를 재실행하지 않았습니다.
- 집계·원본 자료: [Wiki 테스트 결과](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Tests).

상세 구현: [수정](docs/02-api/event-modification.md) · [삭제](docs/02-api/event-deletion.md) · [취소](docs/02-api/event-cancellation.md) · [모집 상태 전환·감사](docs/02-api/event-lifecycle-and-audit.md)

</details>

<details>
<summary>출석</summary>

<!-- 작성 담당: 윤태형 -->

**구현 기능·시연 흐름**

| 항목 | 내용 |
| --- | --- |
| API | `POST /api/v1/attendances` 출석, `GET /attendances/today` 오늘 현황, `GET /attendances?month=` 월별 조회 |
| 하는 일 | 서버 KST 업무일 기준으로 하루 한 번 출석을 인정하고, 일일 보상과 연속 출석 단계 보상(초기 설정 7일·14일·28일)을 응모권으로 지급한다. |
| 핵심 규칙 | 같은 날 다시 눌러도 새로 기록하거나 지급하지 않고 확정된 결과를 `created=false`로 돌려준다. 연속 출석은 매월 초기화되고, 같은 달 같은 단계의 보상은 한 번만 지급한다. |
| 시연 흐름 | 출석 버튼 → 일일 보상 응모권 지급 → 오늘 현황과 월별 달력의 연속 일수·단계 달성 확인 → 같은 날 다시 눌러 중복 지급이 없는지 확인 |

```mermaid
sequenceDiagram
    autonumber
    participant C as 클라이언트
    participant RL as RateLimitInterceptor
    participant S as AttendanceService<br/>트랜잭션 없음
    participant R as AttendanceRecorder<br/>READ_COMMITTED
    participant T as TicketGrantService<br/>MANDATORY
    participant DB as MySQL

    C->>RL: POST /attendances
    alt 사용자 한도 초과
        RL-->>C: 429 + Retry-After
    else 통과
        RL->>S: attend(사용자)
    end
    S->>S: 요청 시각을 한 번만 읽는다
    S->>R: record(사용자, 요청 시각)
    R->>DB: KST 업무일의 기존 출석 조회

    alt 오늘 이미 출석
        R-->>S: 저장된 결과 created=false
    else 첫 출석
        R->>DB: 일일 보상 정책 조회 (없으면 ATTENDANCE-001)
        R->>DB: 출석 INSERT, UNIQUE 사용자+날짜
        R->>DB: 월 연속 현황 FOR UPDATE 후 연속 일수와 새로 도달한 단계 계산
        R->>DB: 일일 보상 청구 INSERT
        R->>T: grant(일일 청구)
        T->>DB: 응모권 + GRANT 이력 INSERT
        loop 새로 도달한 단계마다
            R->>DB: 단계 보상 청구 INSERT
            R->>T: grant(단계 청구)
        end
        R-->>S: created=true, 보상 목록
    end

    opt 같은 날 동시 요청으로 UNIQUE 위반이나 교착
        S->>R: 새 트랜잭션에서 한 번 재처리
        R-->>S: 먼저 확정된 출석을 반환
    end

    S-->>C: 201 처음 출석, 200 이미 출석
```

**설계·기술 선택과 사용 이유**

- **한 트랜잭션에서 확정**: 출석 기록, 보상 청구, 응모권 지급을 함께 커밋하거나 함께 롤백해 "출석은 됐는데 보상이 빠지는" 일을 막는다.
- **바깥 서비스는 트랜잭션 없이(`NOT_SUPPORTED`)**: 안쪽 트랜잭션이 UNIQUE 위반이나 교착으로 롤백되면 그 트랜잭션은 다시 쓸 수 없다. 바깥에서 새 트랜잭션으로 한 번 더 처리해 먼저 확정된 출석을 돌려준다.
- **요청 시각을 한 번만 읽음**: 잠금을 기다리는 사이 KST 자정이 지나도 다음 날 출석으로 바뀌지 않는다.
- **동시성**: `(user_id, 출석일)` UNIQUE가 같은 날 중복을 막고, 월별 연속 현황 행을 `FOR UPDATE`로 잠가 같은 사용자의 동시 요청을 직렬화한다.

**문제와 해결 과정**

- 같은 날 연타·동시 요청과 자정 경계에서 이중 지급 위험 → UNIQUE 제약을 최종 방어선으로 두고, 위반이나 교착이면 새 트랜잭션에서 한 번만 재처리해 먼저 확정된 결과를 돌려준다.
- 호출자 트랜잭션에 합류하면 롤백 표시가 남아 재처리를 할 수 없었다 → 출석을 독립된 업무 단위로 분리했다.
- 연속 일수가 요청이 처리된 순서에 따라 달라지지 않도록 출석 기록(날짜)에서 다시 계산한다(순서가 뒤바뀐 출석 테스트로 검증).

참고: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#attendance)

</details>

<details>
<summary>미션</summary>

<!-- 작성 담당: 윤태형 -->

**구현 기능·시연 흐름**

| 항목 | 내용 |
| --- | --- |
| API | `GET /missions` 목록, `GET /missions/{id}` 상세, `POST /missions/{id}/submissions` 제출, `GET /missions/{id}/submissions/me` 내 제출 내역 |
| 하는 일 | 퀴즈·설문 제출을 받아 채점하고, 완료하면 보상 청구와 응모권 지급까지 한 트랜잭션에서 처리한다. |
| 핵심 규칙 | 퀴즈는 문항별 정답을 누적한다(한 번에 모두 보낼 필요 없음). 설문은 필수 문항에 모두 답해야 완료된다. 이미 완료한 미션을 다시 제출해도 추가 보상이 없다. 서버 도착 시각이 운영 기간 `[시작, 종료)` 안일 때만 받는다. |
| 시연 흐름 | 미션 목록 → 퀴즈 오답 제출(미완료) → 정답 제출(완료, 응모권 1장) → 같은 미션 재제출(추가 보상 없음) → 내 제출 내역 |

```mermaid
sequenceDiagram
    autonumber
    participant C as 클라이언트
    participant RL as RateLimitInterceptor
    participant S as MissionSubmissionService<br/>트랜잭션 없음
    participant R as MissionSubmissionRecorder<br/>READ_COMMITTED
    participant T as TicketGrantService<br/>MANDATORY
    participant DB as MySQL

    C->>RL: POST /missions/{id}/submissions<br/>Idempotency-Key
    alt 사용자 또는 미션별 한도 초과
        RL-->>C: 429 + Retry-After
    else 통과
        RL->>S: submit(사용자, 미션, 제출 ID, 답변)
    end
    S->>R: record
    R->>DB: 같은 제출 ID 조회
    alt 이미 처리된 제출 ID
        R->>R: 처음 요청 해시와 비교
        R-->>C: 같으면 저장된 결과 200, 다르면 409
    else 새 제출
        R->>DB: 미션 조회와 기존 완료 여부 확인
        R->>R: 서버 수신 시각이 운영 기간 안인지 판정 (밖이면 MISSION-004)
        R->>R: 채점 (퀴즈는 문항별 정답 누적, 설문은 필수 답변 검증)
        R->>DB: 제출 + 답변 INSERT, 요청 해시 저장
        R->>DB: 새로 맞힌 문항 진행 기록 (문항 ID 순서)
        opt 이번 제출로 완료
            R->>DB: 보상 청구 INSERT, UNIQUE 사용자+미션
            R->>T: grant(미션 청구)
            T->>DB: 응모권 + GRANT 이력 INSERT
        end
        R-->>S: 제출 결과 + reward (완료 때만)
    end

    opt UNIQUE 위반이나 잠금 실패
        S->>R: 새 트랜잭션에서 한 번 재처리
        R-->>S: 먼저 확정된 결과
    end
    Note over R,DB: 지급이 실패하면 제출, 답변, 정답 진행, 청구가 모두 롤백된다
    S-->>C: 201 새 제출, 200 재전송
```

**설계·기술 선택과 사용 이유**

- **제출 ID(`Idempotency-Key`)를 PK로**: 별도 멱등 테이블 없이 같은 키의 동시 요청을 PK가 직렬화한다.
- **처음 요청의 SHA-256 해시로 재전송 판정**: 이미 정답으로 완료한 문항의 답변은 저장하지 않아 저장된 답변만으로는 처음 요청을 복원할 수 없다. 해시로 같은 요청인지 다른 요청인지(409)를 가린다.
- **유일 제약 세 개로 동시 완료 직렬화**: 제출 PK, `(사용자, 퀴즈 문항)`, `(사용자, 미션)` 보상 청구.
- **한 트랜잭션**: 지급이 실패하면 제출·답변·정답 진행·청구가 모두 롤백된다.

**문제와 해결 과정**

- 재전송 본문 비교를 저장된 답변으로 하면 처음 요청을 복원하지 못해 다른 요청을 같은 요청으로 판정할 수 있었다 → 처음 요청의 해시 컬럼을 새 마이그레이션으로 추가했다.
- 같은 사용자의 동시 요청이 서로의 문항 행을 반대 순서로 기다리는 교착 위험 → 새로 맞힌 문항을 문항 ID 순서로 기록하도록 고정했다.
- 단답형 채점이 입력 공백이나 유니코드 조합 차이로 어긋나지 않도록 정규화(NFC, 앞뒤 공백 제거 등) 규칙을 두었다.

참고: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#mission)

</details>

<details>
<summary>응모권</summary>

<!-- 작성 담당: 윤태형 -->

**구현 기능·시연 흐름**

| 항목 | 내용 |
| --- | --- |
| 하는 일 | 응모권 한 장은 `tickets` 한 행이고, 상태가 바뀔 때마다 `ticket_histories`에 이력을 한 줄 추가한다. 지급은 출석·미션 보상 청구와, 차감은 응모와, 반환은 이벤트 취소와 같은 트랜잭션에서 처리하고, 만료는 워커가 처리한다. |
| API | 사용자 `GET /tickets/me`(보유), `GET /tickets/histories/me`(내역), 관리자 `GET /admin/ticket-grants`·`ticket-histories`·`ticket-refunds`·`users/{id}/tickets/consistency`·`tickets/inconsistencies` |
| 핵심 규칙 | 한 장은 두 번 쓰이거나 두 번 만료되지 않는다. 같은 요청을 다시 보내면 확정된 결과를 돌려준다. 등급(브론즈·실버·골드)은 지급 때 정해져 바뀌지 않는다. |
| 시연 흐름 | 출석·미션으로 지급 → 보유 응모권 확인 → 응모로 차감(등급별) → 내역에서 지급·사용 확인 → 관리자 응모권 이력·정합성 조회 |

**차감(응모)과 지급(출석·미션)**

```mermaid
sequenceDiagram
    autonumber
    participant Caller as 호출자 서비스<br/>응모·출석·미션
    participant Grant as TicketGrantService<br/>MANDATORY
    participant Use as TicketUseService<br/>MANDATORY
    participant DB as MySQL

    Note over Caller,DB: 모두 호출자가 연 트랜잭션 안에서 실행된다

    rect rgb(240, 248, 255)
    Caller->>Grant: grant(보상 청구)
    Grant->>DB: 청구 행 FOR UPDATE, 사용자·수량 확인
    alt 이미 지급된 청구
        Grant-->>Caller: 기존 응모권 반환 replayed=true
    else 처음 지급
        Grant->>DB: tickets INSERT + GRANT 이력 INSERT 버전 1
        Grant-->>Caller: 지급 결과
    end
    end

    rect rgb(255, 248, 240)
    Caller->>Use: use(응모 ID, 등급별 장수)
    Use->>DB: 같은 응모 ID의 USE 이력 조회
    alt 이미 처리된 응모 ID
        Use-->>Caller: 요청이 같으면 기존 결과, 다르면 거절
    else 처음 차감
        Use->>DB: 등급별 사용 가능 후보를 만료 빠른 순으로 잠금 없이 조회
        Use->>DB: 후보를 PK 순서로 한 장씩 FOR UPDATE 후 최신 값으로 재판정
        Use->>DB: 만료가 섞였는지 처리 시각으로 다시 확인
        Use->>DB: 상태 SPENT 로 UPDATE + USE 이력 INSERT
        Use-->>Caller: 차감 결과
    end
    end
```

**반환(이벤트 취소)과 만료(워커)**

```mermaid
sequenceDiagram
    autonumber
    participant Cancel as 이벤트 취소 서비스
    participant Refund as TicketRefundService<br/>MANDATORY
    participant Worker as 만료 워커<br/>전용 스레드, 60초 간격
    participant Expire as TicketExpiryService<br/>REQUIRES_NEW
    participant DB as MySQL

    Cancel->>Refund: refund(응모 ID) 응모마다 호출
    Refund->>DB: 응모의 USE 이력과 기존 반환 이력 조회
    alt 모두 이미 반환됨
        Refund-->>Cancel: 기존 결과 replayed=true
    else 반환할 것이 있음
        Refund->>DB: 응모권을 PK 순서로 FOR UPDATE
        Refund->>DB: 사용 직후 상태인지 판정
        Refund->>DB: RETURNED + 새 만료 시각 UPDATE, REFUND 이력 INSERT 원본 USE 연결
        Refund-->>Cancel: 반환 결과
    end

    loop 폴링마다 최대 50묶음, 묶음당 100장
        Worker->>Expire: expireDue(100)
        Expire->>DB: 만료 시각이 지난 응모권을 FOR UPDATE SKIP LOCKED
        Note right of Expire: 다른 처리가 잠근 응모권은 기다리지 않고 건너뜀
        Expire->>DB: EXPIRED UPDATE + EXPIRE 이력 INSERT
        Expire-->>Worker: 처리 장수
    end
```

**설계·기술 선택과 사용 이유**

- **현재 상태와 변경 기록 분리**: `tickets`는 현재 값, `ticket_histories`는 추가만 가능한 이력이다. `(ticket_id, 버전)` UNIQUE가 같은 버전의 중복 처리를, 반환 원본 UNIQUE가 같은 사용의 이중 반환을 DB에서 막는다.
- **차감은 후보를 잠그지 않고 읽은 뒤 PK로 한 장씩 잠금**: 범위 `FOR UPDATE`는 `REPEATABLE_READ`에서 gap lock을 만들 수 있다. PK 단위 잠금은 격리 수준과 상관없이 같은 동작을 한다(측정: PK 단위는 두 격리 수준 모두 약 565ms, 범위 잠금은 `REPEATABLE_READ`에서 770ms).
- **반환은 응모권 상태로 판정하고 상태가 이상할 때만 반환 이력을 잠금 읽기**: 이력이 없는 구간을 읽기 잠금으로 잡으면 서로 다른 사용자의 반환이 교착한다(측정: 항상 잠금 읽기는 60회 중 60회 교착, 현재 방식은 0회).
- **만료는 `FOR UPDATE SKIP LOCKED`**: 서버 여러 대의 워커가 겹쳐도 같은 응모권을 두 번 만료하지 않고, 다른 처리가 잠근 것은 기다리지 않고 건너뛴다(측정: 워커 4대에서 약 3.5초, 일반 `FOR UPDATE`는 20.9초).
- **응모권 서비스는 `MANDATORY`**: 호출자(응모·출석·미션)의 트랜잭션 안에서만 실행해 업무 기록과 함께 커밋·롤백된다.

**문제와 해결 과정**

- **반환 동시 처리의 교착**: 반환 이력을 항상 잠금 읽기로 보자 `REPEATABLE_READ`에서 서로 다른 사용자의 동시 반환이 교착했다. MySQL 교착 기록으로 원인을 확인했다 — `ticket_histories`의 `uq_ticket_histories_refund_source` 인덱스에서 두 트랜잭션이 아직 없는 구간을 읽기 잠금(S)으로 먼저 잡고 각자 그 구간에 INSERT하려다 서로 막혔다. 상태가 이상할 때만 확인하도록 좁혀 해결했다.
- **범위 잠금의 gap lock**: 후보 범위를 `FOR UPDATE`로 잠그는 대신 PK 단위 잠금으로 바꿨다.
- **잠금 순서 뒤집힘**: 만료된 응모권을 다른 것으로 바꿔 다시 잠그면 앞서 쥔 잠금과 순서가 뒤집혀 교착할 수 있어, 만료가 섞였으면 실패시키고(`TICKET-006`) 바꿔 잠그지 않는다.

**테스트 결과 (직접 측정)**

같은 서비스 코드에 잠금 방식만 바꿔 채택한 방식과 검토한 대안을 같은 조건에서 비교했다. 대안(범위 잠금, 항상 잠금 읽기, 일반 `FOR UPDATE`)은 비교용으로 만든 변형이고, 측정 코드는 저장소에 포함하지 않았다. 로컬 단일 머신(i5-1240P, RAM 15.7GB, Docker MySQL 8.4 Testcontainers, JDK 21)의 상대 비교이며 운영 성능이 아니다.

| 측정 | 실행일 | 기준 커밋 | 실행 명령 | 결과 |
| --- | --- | --- | --- | --- |
| 반환 이력 잠금 | 2026-10-16 | dev `9bc9ea00` | `GETDDO_BENCH=1 ./gradlew :storage:db:integrationTest --tests '*RefundDeadlockBenchmarkTest*'` | 채택 방식 교착 0건, 대안 교착 |
| 만료 워커 잠금 | 2026-10-16 | dev `9bc9ea00` | `GETDDO_BENCH=1 ./gradlew :storage:db:integrationTest --tests '*LockingBenchmarkTest*'` | 두 방식 모두 중복·누락 0건 |
| 차감 잠금 | 2026-10-16 | dev `9bc9ea00` | 위와 같음 | 두 방식 모두 실패 0건 |
| 교착 기록 캡처 | 2026-10-16 | `feat/GD-208` `18f00dda` | `GETDDO_BENCH=1 ./gradlew :storage:db:integrationTest --tests '*DeadlockCaptureTest*'` | 대안은 1회차에 교착, 채택 방식은 10회 0건 |

| 비교 | 조건 | 채택한 방식 | 대안 |
| --- | --- | --- | --- |
| 반환 이력 잠금 읽기(`REPEATABLE_READ`) | 서로 다른 사용자 4명 동시 반환, 60회 반복 | 교착 0회 | **항상 잠금 읽기: 60회 중 60회 교착**(반환 240건 중 180건 실패) |
| 반환 이력 잠금 읽기(`READ_COMMITTED`) | 같은 조건 | 교착 0회 | 교착 0회(구간 잠금이 없어 차이 없음) |
| 만료 워커(4,000장, 배치 100장) | 워커 2대 / 4대 | `SKIP LOCKED`: 약 7.5초 / 약 3.5초 | 일반 `FOR UPDATE`: 24.1초 / 20.9초(워커를 늘려도 빨라지지 않음) |
| 차감 잠금(`READ_COMMITTED`) | 같은 사용자 차감 4 + 지급 4 동시, 12회 | PK 단위 568ms | 범위 `FOR UPDATE` 568ms(차이 없음) |
| 차감 잠금(`REPEATABLE_READ`) | 같은 조건 | PK 단위 564ms | 범위 `FOR UPDATE` 770ms(약 37% 느림, 반복 간 범위가 겹쳐 크기는 단정하지 않음) |

**교착 원인(MySQL 교착 기록)**: `ticket_histories`의 `uq_ticket_histories_refund_source` 인덱스에서 두 트랜잭션이 아직 없는 구간을 읽기 잠금(S)으로 먼저 잡은 뒤 각자 그 구간에 INSERT(삽입 의도 잠금)하려다 서로 막혔고, 한쪽이 롤백됐다. 만료 워커는 모든 경우 처리 4,000장과 만료 이력 4,000건이 일치했다.



참고: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#ticket)

</details>

<details>
<summary>이벤트 응모</summary>

<!-- 작성 담당: 윤태형 -->

**구현 기능·시연 흐름**

| 항목 | 내용 |
| --- | --- |
| API | `POST /api/v1/events/{id}/entries` 응모, `GET /events/{id}/entries/me`·`GET /users/me/entries` 내 응모 내역, `GET /events/{id}/eligibility` 자격 확인, `GET /events/{id}/statistics` 현황 |
| 하는 일 | 응모 기록, 응모권 차감, 차감 이력, 응모자 누적 수량을 한 트랜잭션에서 확정한다. |
| 핵심 규칙 | 응모 ID(`Idempotency-Key`)가 곧 PK라서 같은 요청을 다시 보내면 최초 결과를 돌려준다(같은 키에 다른 내용이면 409). 마감은 차감 뒤와 커밋 직전, 두 번 확인한다. 업무 거절은 `REJECTED` 행으로 남기고 같은 키로 다시 보내면 처음과 같은 오류를 돌려준다. |
| 시연 흐름 | 자격 확인 → 등급별 응모권 수량을 골라 응모 → 접수 결과·잔여 응모권·사용 이력 일치 확인 → 같은 키로 재전송(중복 차감 없음) → 마감 이후 응모 거절과 내 응모 내역의 거절 기록 |

```mermaid
sequenceDiagram
    autonumber
    participant C as 클라이언트
    participant RL as RateLimitInterceptor
    participant S as EntryService<br/>트랜잭션 없음
    participant R as EntryRecorder<br/>READ_COMMITTED
    participant U as TicketUseService<br/>MANDATORY
    participant J as EntryRejectionRecorder<br/>REQUIRES_NEW
    participant DB as MySQL

    C->>RL: POST /events/{id}/entries<br/>Idempotency-Key
    alt 사용자 또는 이벤트별 한도 초과
        RL-->>C: 429 + Retry-After
    else 통과
        RL->>S: enter(응모 요청)
    end
    S->>R: record
    R->>DB: 같은 응모 ID 조회
    alt 이미 있는 응모 ID
        R-->>C: 접수 건은 기존 영수증, 거절 건은 처음과 같은 오류, 내용이 다르면 409
    else 새 응모
        R->>DB: 이벤트 행 FOR SHARE
        R->>R: 멤버십, 기간, 요청 형태, 1회 및 누적 상한 검증
        R->>DB: 참여자 행 PK FOR UPDATE (없으면 삽입)
        R->>DB: 응모 INSERT
        R->>U: use(응모 ID, 등급별 장수)
        U->>DB: 응모권 PK 잠금, SPENT UPDATE, USE 이력 INSERT
        R->>DB: 참여자 누적 차감 수량 증가
        R->>R: 마감 재확인, 커밋 직전 beforeCommit 에서 한 번 더 확인
        R-->>S: 영수증
    end

    opt 업무 거절 (마감, 멤버십 미달, 응모권 부족 등)
        R--xS: 예외 + 트랜잭션 롤백
        S->>J: 거절 이력 기록
        J->>DB: REJECTED 행 INSERT, 같은 응모 ID
        S-->>C: 처음 오류를 그대로 응답
    end
    opt 같은 키의 동시 요청으로 UNIQUE 위반이나 교착
        S->>R: 새 트랜잭션에서 한 번 재처리
    end

    S-->>C: 201 새 응모, 200 재전송
```

**설계·기술 선택과 사용 이유**

- **이벤트 행은 `FOR SHARE`로 읽음**: 응모끼리 서로 막지 않고, 추첨 준비의 `FOR UPDATE`만 진행 중인 응모의 커밋을 기다린다. 쓰기 잠금이면 같은 이벤트의 모든 응모가 줄을 서 인기 이벤트에서 병목이 된다(측정: 같은 이벤트에 64명이 동시 응모할 때 처리량 102건/초 대 23건/초, 약 4.4배).
- **잠금 순서 고정**: `이벤트(FOR SHARE)` → `참여자(PK FOR UPDATE)` → `응모권`으로 항상 같은 순서로 잡아 교착을 피한다.
- **멱등 키 = PK**: 별도 멱등 테이블 없이 같은 키의 동시 요청을 PK가 직렬화한다.
- **마감 이중 확인**: 차감 뒤 한 번, 커밋 직전에 한 번 더 확인한다. 추첨 준비는 진행 중인 응모의 커밋을 기다렸다가 후보에 넣으므로, 커밋 직전 확인이 없으면 마감 직후 커밋된 응모가 후보가 될 수 있다.
- **거절 이력은 `REQUIRES_NEW`**: 거절은 응모 트랜잭션을 롤백시키므로 거절 기록을 별도 트랜잭션에서 남기고 원래 오류를 다시 던진다.

**문제와 해결 과정**

- **마감 판정의 틈**: 확인과 DB의 커밋 확정 사이의 아주 짧은 간격은 어떤 구현으로도 없앨 수 없다. 공용 도메인 명세에 "마감 판정 예외"로 승인되어 있고, 간격 크기를 보장하는 수치는 문서에서 제거했다.
- **거절 건 저장**: 같은 트랜잭션에 거절을 쓸 수 없어 전용 서비스를 `REQUIRES_NEW`로 분리했다. 같은 키의 동시 요청은 PK가, 거절 기록의 중복 키는 먼저 확정된 결과를 다시 읽어 돌려주는 것으로 처리했다.
- **이벤트 행 잠금 선택**: 쓰기 잠금과 공유 잠금을 같은 조건에서 비교해 공유 잠금을 채택했다(아래 측정).

**테스트 결과 (직접 측정)**

| 측정 | 실행일 | 기준 커밋 | 실행 명령 | 결과 |
| --- | --- | --- | --- | --- |
| 이벤트 행 잠금 | 2026-10-16 | dev `9bc9ea00` | `GETDDO_BENCH=1 ./gradlew :storage:db:integrationTest --tests '*LockingBenchmarkTest*'` | 두 방식 모두 실패 0건 |

같은 서비스 코드에 이벤트 행 잠금 방식만 바꿔 같은 조건에서 비교했다(대안은 비교용 변형, 측정 코드는 저장소에 포함하지 않음). 로컬 단일 머신(i5-1240P, RAM 15.7GB, Docker MySQL 8.4 Testcontainers)의 상대 비교다.

| 비교 | 조건 | `FOR SHARE`(채택) | `FOR UPDATE` |
| --- | --- | --- | --- |
| 이벤트 행 잠금 | 서로 다른 사용자 64명이 같은 이벤트에 동시 응모, 동시 8, 8회 반복 | 중앙값 626ms, 102건/초, 건당 p95 102ms | 중앙값 2,774ms, 23건/초, 건당 p95 380ms (**약 4.4배 느림**) |

반복마다 범위가 겹치지 않았다(523~775ms 대 2,525~2,852ms).



참고: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#entry)

</details>

<details>
<summary>요청 빈도 제한</summary>

<!-- 작성 담당: 윤태형 -->

**구현 기능·시연 흐름**

| 항목 | 내용 |
| --- | --- |
| 적용 대상 | 응모(사용자 20회/분, 같은 이벤트 10회/분), 출석(5회/분), 미션 제출(20회/분, 같은 미션 5회/분), 게임 결과 제출(10회/분) |
| 하는 일 | 사용자별로 일정 시간 안에 보낼 수 있는 요청 수를 제한한다. 넘으면 `429`, 오류 코드 `COMMON-006`, 다시 시도할 수 있을 때까지의 초를 `Retry-After`로 돌려준다. 거절은 비즈니스 로직보다 먼저 일어나 DB 작업(응모권 차감, 거절 이력 저장)을 하지 않는다. |
| 핵심 규칙 | 사용자는 `X-User-ID`로 구분한다. 고정 창 방식이라 창 안에서 한도까지 허용하고 창이 끝나면 다시 센다. 제한기가 실패하면 요청을 막지 않고 통과시키고 경고 로그를 남긴다. |

```mermaid
sequenceDiagram
    autonumber
    participant C as 클라이언트
    participant RL as RateLimitInterceptor
    participant L as RateLimiter<br/>메모리 또는 Redis
    participant API as Controller

    C->>RL: 요청 + X-User-ID
    RL->>RL: @RateLimited 정책 확인, 사용자 ID 형식 확인
    alt 정책 없음 또는 ID 없음/형식 오류
        RL->>API: 제한하지 않고 통과 (기존 사용자 검증이 처리)
    else 제한 대상
        RL->>L: tryAcquire(정책+사용자, 한도, 창)
        opt 범위별 한도가 있음 (이벤트, 미션)
            RL->>L: tryAcquire(정책+사용자+범위, 범위별 한도, 창)
        end
        alt 한도 초과
            RL-->>C: 429 COMMON-006 + Retry-After
        else 허용
            RL->>API: 통과
        end
    end
    Note over RL,L: 제한기가 실패하면 경고 로그만 남기고 통과시킨다 (fail-open)
```

**설계·기술 선택과 사용 이유**

- 서버 메모리 구현은 서버가 한 대일 때만 정확하다. 서버가 여러 대이면 한도가 서버마다 따로 적용되므로 `RateLimiter` 인터페이스 뒤에서 저장소를 환경변수(`RATE_LIMIT_STORE`)로 고르게 했다.
- Redis 구현은 Lua 스크립트 하나로 횟수 증가와 만료 설정을 원자적으로 실행하고, 창은 Redis 서버 시계로 잰다. 서버 사이의 시계 차이와 동시 요청에 영향받지 않는다.
- 제한 때문에 응모·출석이 모두 멈추는 것보다 낫다는 판단으로 저장소 장애 시에는 요청을 통과시킨다(fail-open).

**문제와 해결 과정**

- 서버 메모리 구현의 "서버별로 따로 센다" 한계 → Redis 구현(PR #107, 리뷰 중)으로 같은 키를 하나로 센다. 서버 두 대를 흉내 낸 테스트와 동시 50건 요청에서 정확한 횟수를 확인했다.
- 연결이 느리거나 끊겨도 요청이 오래 붙잡히지 않게 연결 500ms·명령 300ms 제한 시간을 두었다.
- 고정 창이라 창 경계에서 최대 한도의 두 배까지 허용될 수 있다는 한계가 있고, 슬라이딩 윈도·토큰 버킷 등과의 비교는 추후 예정이다.


참고: [요청 빈도 제한 문서](docs/02-api/rate-limit.md)

</details>

<details>
<summary>추첨·결과 공개</summary>

<!-- 작성 담당: 석종수 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

기존 참고 자료: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#drawing) — 해당 문서의 기준 시점과 현재 구현을 확인한 뒤 작성합니다.

</details>

<details>
<summary>지급 전 어뷰징 차단 기록</summary>

<!-- 작성 담당: 석종수 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

</details>

<details>
<summary>알림</summary>

<!-- 작성 담당: 전민규 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

기존 참고 자료: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#notification) — 해당 문서의 기준 시점과 현재 구현을 확인한 뒤 작성합니다.

</details>

<details>
<summary>게임</summary>

<!-- 작성 담당: 전민규 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

기존 참고 자료: [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#game) — 해당 문서의 기준 시점과 현재 구현을 확인한 뒤 작성합니다.

</details>

<details>
<summary>백오피스·배너</summary>

<!-- 작성 담당: 전민규 -->

담당자 작성 예정입니다.

- **구현 기능·시연 흐름**: 작성 예정
- **설계·기술 선택과 사용 이유**: 작성 예정
- **문제와 해결 과정**: 작성 예정
- **테스트 결과**: 실행일·기준 커밋·명령·성공/실패·검증 범위 작성 예정

</details>

## 실행 방법

Docker Compose 실행에는 Docker가 필요합니다. 터미널에서 애플리케이션이나 Gradle 작업을 실행하려면 JDK 21도 준비합니다. Gradle은 저장소의 Wrapper를 사용합니다.

### Docker Compose

저장소 루트에서 `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`에 각각 로컬 비밀번호를 입력합니다. DB 이름과 일반 계정은 예시의 `getddo`를 사용합니다. `.env`는 Git과 Docker 빌드 컨텍스트에서 제외됩니다.

```bash
docker compose up --build -d
```

앱은 `http://localhost:8080`, MySQL 8.4는 `localhost:3306`에서 접근할 수 있습니다. Compose는 `local` Spring 프로필을 활성화하고, `application-local.yaml`에 정의된 MySQL JDBC 연결에 DB 환경변수를 전달합니다. MySQL 데이터는 Compose 볼륨에 유지됩니다. 종료할 때는 `docker compose down`을 사용합니다.

앱 시작 시 Flyway가 `storage:db/src/main/resources/db/migration/<도메인>/`의 SQL을 버전 순서대로 적용합니다. 마이그레이션이 추가될 때마다 버전 번호와 개수가 바뀌어 PR끼리 이 문단에서 충돌하므로 여기에는 적지 않습니다. 현재 목록은 해당 디렉터리를, 각 버전이 추가한 테이블·인덱스와 API 대응은 [API·DB 대응](docs/03-database/api-schema-mapping.md)을 확인합니다. 초기 스키마(44개 테이블)는 가장 앞의 마이그레이션 묶음이 생성하며, 이벤트의 논리 삭제 시각, 출석 기준일·사용자별 하루 1회 UNIQUE와 외래 키는 각 테이블의 초기 생성 정의에 포함되어 있습니다. Docker Desktop을 WSL에서 사용하는 경우 WSL 연동을 활성화해야 합니다.

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

`PR 제목 검사`는 [공용 PR 제목 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/01-conventions/pull-request.md)에 맞는지 별도로 확인합니다. PR 생성·수정·커밋 추가 시 실행하며, 제목·본문만 수정하면 Gradle 빌드는 실행하지 않습니다. 제목 오류로 병합을 막으려면 main/dev Ruleset의 필수 상태 검사에 `PR 제목 검사`를 등록합니다.

<details>
<summary>기존 전체 테스트 실행 기록 (2026-10-16, dev 15f2d95c)</summary>

2026-10-16 KST, dev `15f2d95c` 기준으로 전체 `test build`를 실행해 **BUILD SUCCESSFUL**을 확인했습니다.

| 구분 | 실행 수 | 결과 |
| --- | ---: | --- |
| DB 없는 단위·MVC·설정 검증 | 1,195 | 실패·오류·건너뜀 0 |
| Testcontainers MySQL 통합 검증 | 1,009 | 실패·오류·건너뜀 0 |
| **합계** | **2,204** | **성공** |

실제 잠금·중복 처리·마감 경계·롤백·공개 버전과 API 계약을 검증합니다. [Wiki 테스트 결과](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Tests)에 실행 명령·모듈/기능별 결과·원본 JSON/CSV를 정리했습니다. 같은 날 S3Mock에서 확인한 객체 입출력 17개는 이 집계와 별도입니다.

이 결과는 로컬 기능 검증이며 부하 처리량·실제 AWS 배포·FE 전체 흐름·S3/CloudFront 연동 완료를 뜻하지 않습니다. 후속 작업의 원격 CI 상태와 실제 연동 검증은 별도로 확인합니다.

</details>
